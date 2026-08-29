package com.kronos.chiron;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.kronos.chiron.course.ChironCoursePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ChironCoursePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
