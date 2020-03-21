/*
 * Copyright 2017 Actian Corporation
 */
package com.actian.zen.tasklist;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;


import java.io.File;
import java.util.ArrayList;

public class TaskListActivity extends AppCompatActivity {
    private static final String TAG = "TaskListActivity";
    private TaskTable mTable;
    private int mTitleSize;
    private ListView mTaskListView;
    private TaskListAdapter mAdapter;
    private static boolean inited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);

        if (! inited) {
            // Do one time initialization of ZenDB library interface.
            ZenDBHelper.Initialize(this);
            inited = true;
        }
        // specify the path for the database table used to store tasks.
        // 1) (local data file) Specify a local path.
        File filesDir = this.getFilesDir();
        File dbfilepath = new File(filesDir, "tasklist.btr");
      //  mTable = new TaskTable(dbfilepath.getAbsolutePath());

        // 2) (remote data file) The data file could be hosted on a remote machine
        // running the Actian Zen PSQL database engine. A URI (uniform resource
        // identifier) for the remote data file could be specified as shown here.
        //
        // Example: using classic security to a Zen PSQL server engine on Windows.
        // mTable = new TaskTable("btrv://user@192.168.1.20/phonedemo?dbfile=tasklist.btr&pwd=passwd");
        // Example: using database security to a Zen PSQL server engine.
        // mTable = new TaskTable("btrv://user@192.168.1.20/phonedemo1?dbfile=tasklist.btr&pwd=passwd");
           mTable=new TaskTable("btrv://user@192.168.1.20/phonedemo?dbfile=tasklist.btr&pwd=passwd");
        //
        // The application would need to handle exceptions due to the remote
        // machine not being accessible etc.
        // The application also needs to secure the credentials that have to be used for
        // remote access.
        mTitleSize = mTable.getTitleSize();
        mTaskListView = (ListView) findViewById(R.id.list_task);

        updateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_task:
                final EditText taskEditText = new EditText(this);
                taskEditText.setFilters(new InputFilter[] {
                        new InputFilter.LengthFilter(mTitleSize-1)
                });
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("New task")
                        .setMessage("Please enter a new task.")
                        .setView(taskEditText)
                        .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String title = String.valueOf(taskEditText.getText());
                                Log.v(TAG, String.format("Insert: %s", title));
                                TaskRecord task = new TaskRecord(title);
                                TaskCursor cursor = new TaskCursor(mTable);
                                cursor.insert(task);
                                cursor.close();
                                updateUI();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .create();
                dialog.show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void deleteTask(View view) {
        View parent = (View) view.getParent();
        TextView taskTextView = (TextView) parent.findViewById(R.id.task_title);
        String title = String.valueOf(taskTextView.getText());
        TaskCursor cursor = new TaskCursor(mTable);
        // The 'id' field of the task record was associated with the view by
        // calling 'setTag'.  Retrieve it using 'getTag'.
        cursor.deleteById((int) parent.getTag());
        cursor.close();
        Log.v(TAG, String.format("Delete: %d %s", (int) parent.getTag(), title));
        updateUI();
    }

    private void updateUI() {
        // taskList: a list of tasks to be displayed.
        ArrayList<TaskRecord> taskList = new ArrayList<>();
        // Populate taskList by iterating through the database
        // table.
        TaskCursor cursor = new TaskCursor(mTable);
        TaskRecordIterator iter = new TaskRecordIterator(cursor);
        while (iter.hasNext()) {
            TaskRecord task = iter.next();
            Log.v (TAG, String.format("updateUI: %d %s", task.get_id(), task.getTitle()));
            taskList.add(task);
        }
        Log.v (TAG, "Close table");
        cursor.close();

        // Hand off the task list to the ListView via a custom adapter.
        if (mAdapter == null) {
            mAdapter = new TaskListAdapter(taskList);
            mTaskListView.setAdapter(mAdapter);
        } else {
            mAdapter.setTaskList(taskList);
            mAdapter.notifyDataSetChanged();
        }


    }

    private class TaskListAdapter extends BaseAdapter {

        ArrayList<TaskRecord> mTaskList;

        public TaskListAdapter(ArrayList<TaskRecord> tasklist) {
            mTaskList = tasklist;
        }

        @Override
        public int getCount() {
            return mTaskList.size();
        }

        @Override
        public TaskRecord getItem(int position) {
            return mTaskList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mTaskList.get(position).hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            TaskRecord rec = getItem(position);
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_task, container, false);
            }

            ((TextView) convertView.findViewById(R.id.task_title))
                    .setText(rec.getTitle());
            // associate the task id with the view -- we use this later to look up the task
            // in the database.
            convertView.setTag(rec.get_id());
            return convertView;
        }

        public void setTaskList(ArrayList<TaskRecord> taskList) {
            mTaskList = taskList;
        }
    }

}
