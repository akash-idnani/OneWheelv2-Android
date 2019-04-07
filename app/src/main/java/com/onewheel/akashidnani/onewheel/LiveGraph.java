package com.onewheel.akashidnani.onewheel;

import android.content.Context;
import android.util.AttributeSet;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;

public class LiveGraph extends LineChart {

    private int angleIndex = 1;
    private static int DATA_POINTS_PER_SCREEN = 500;

    public LiveGraph(Context context) {
        super(context);
        initLiveGraph();
    }

    public LiveGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        initLiveGraph();
    }

    public LiveGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initLiveGraph();
    }

    private void initLiveGraph() {
        getAxisLeft().setAxisMinimum(-20);
        getAxisLeft().setAxisMaximum(20);

        LineData lineData = new LineData(
                new LineDataSet(new ArrayList<>(), "Data")
        );
        setData(lineData);
        invalidate();
    }

    public void addDataPoint(float dataPoint) {
        if (angleIndex > DATA_POINTS_PER_SCREEN) {
            getLineData().getDataSetByIndex(0).removeFirst();
        }

        getLineData().addEntry(new Entry(angleIndex, dataPoint), 0);
        getLineData().notifyDataChanged();
        notifyDataSetChanged();
        invalidate();
        angleIndex++;
    }
}
