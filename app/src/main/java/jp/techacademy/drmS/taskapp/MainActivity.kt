package jp.techacademy.drmS.taskapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import io.realm.RealmChangeListener
import io.realm.Sort
import android.content.Intent
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import kotlinx.android.synthetic.main.content_input.view.*
import java.util.*

const val EXTRA_TASK = "jp.techacademy.drmS.taskapp.TASK"

class MainActivity : AppCompatActivity() {

    private lateinit var mTaskAdapter : TaskAdapter
    private var categoryQuery : String = "";

    private lateinit var mRealm: Realm
    private val mRealmListener = object : RealmChangeListener<Realm> {
        override fun onChange(element: Realm) {
            reloadListView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab.setOnClickListener { view ->
            val intent = Intent(this@MainActivity, InputActivity::class.java)
            startActivity(intent)
        }

        // Realmの設定
        mRealm = Realm.getDefaultInstance()
        mRealm.addChangeListener(mRealmListener)

        // ListViewの設定
        mTaskAdapter = TaskAdapter(this@MainActivity)

        // ListViewをタップしたときの処理
        listView1.setOnItemClickListener { parent, _, position, _ ->
            // 入力・編集する画面に遷移させる
            val task = parent.adapter.getItem(position) as Task
            val intent = Intent(this@MainActivity, InputActivity::class.java)
            intent.putExtra(EXTRA_TASK, task.id)
            startActivity(intent)
        }


        // ListViewを長押ししたときの処理
        listView1.setOnItemLongClickListener { parent, _, position, _ ->
            // タスクを削除する
            val task = parent.adapter.getItem(position) as Task

            // ダイアログを表示する
            val builder = AlertDialog.Builder(this@MainActivity)

            builder.setTitle("削除")
            builder.setMessage(task.title + "を削除しますか")

            builder.setPositiveButton("OK"){_, _ ->
                val results = mRealm.where(Task::class.java).equalTo("id", task.id).findAll()

                mRealm.beginTransaction()
                results.deleteAllFromRealm()
                mRealm.commitTransaction()

                val resultIntent = Intent(applicationContext, TaskAlarmReceiver::class.java)
                val resultPendingIntent = PendingIntent.getBroadcast(
                    this@MainActivity,
                    task.id,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(resultPendingIntent)

                reloadListView()
            }

            builder.setNegativeButton("CANCEL", null)

            val dialog = builder.create()
            dialog.show()

            true
        }

        var search : SearchView = search
        search.isSubmitButtonEnabled = true
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextChange(keyword: String?): Boolean {
                categoryQuery = keyword?: ""
                reloadListView()
                return false
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                categoryQuery = query?: ""
                reloadListView()
                return false
            }
        })

        reloadListView()
    }

    private fun reloadListView(){
        // Realmデータベースから、「全てのデータを取得して新しい日時順に並べた結果」を取得
        var taskList : Iterable<Task> = mRealm.where(Task::class.java).findAll().sort("date", Sort.DESCENDING)

        // search keyword (= query) に何か入力されている場合、その文字を含むタスクのみ抽出する
        if(!categoryQuery.contentEquals("")){
            taskList = taskList.filter { it.category.contentEquals(categoryQuery)}

            //実用上はこちらが便利
            //taskList = taskList.filter { it.category.contains(categoryQuery)}
        }

        // 上記の結果を、TaskList としてセットする
        mTaskAdapter.taskList = mRealm.copyFromRealm(taskList)

        // TaskのListView用のアダプタに渡す
        listView1.adapter = mTaskAdapter

        // 表示を更新するために、アダプターにデータが変更されたことを知らせる
        mTaskAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()

        mRealm.close()
    }
}