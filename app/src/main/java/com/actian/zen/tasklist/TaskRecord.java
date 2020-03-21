/*
 * Copyright 2017 Actian Corporation
 */
package com.actian.zen.tasklist;

/**
 * The TaskRecord class encapsulate a single task item.
 */
public class TaskRecord {
    int mId;
    String mTitle;

    TaskRecord(int id_, String title_) {
        mId = id_;
        mTitle = title_;
    }

    TaskRecord(String title_) {
        mId = 0;
        mTitle = title_;
    }

    public int get_id() {
        return mId;
    }

    public String getTitle() {
        return mTitle;
    }
}
