package com.wtw;

import com.google.common.base.Preconditions;
import com.wtw.compression.CompressionManager;
import com.wtw.detectors.GestureDetector;
import com.wtw.event.EventBus;
import com.wtw.event.EventHandler;
import com.wtw.event.EventListener;
import com.wtw.event.events.*;
import com.wtw.filters.Filter;
import com.wtw.timeseries.TimeSeries;
import com.wtw.timeseries.TimeSeriesComparison;
import com.wtw.timewarp.TimeWarpComparisonResults;
import com.wtw.timewarp.TimeWarpManager;
import lombok.Getter;

import java.util.ArrayList;

public class BuiltDevice extends EventListener {
    @Getter
    private final EventBus eventBus;
    private final ArrayList<Filter> filters;
    @Getter
    private final GestureDetector gestureDetector;
    @Getter
    private final CompressionManager compressionManager;
    @Getter
    private final TimeWarpManager timeWarpManager;

    public BuiltDevice(EventBus eventBus, ArrayList<Filter> filters, GestureDetector gestureDetector, CompressionManager compressionManager, TimeWarpManager timeWarpManager) {
        Preconditions.checkNotNull(gestureDetector, "Must define a gesture detector.");
        this.filters = filters;
        this.gestureDetector = gestureDetector;
        this.eventBus = eventBus;
        this.compressionManager = compressionManager;
        this.timeWarpManager = timeWarpManager;
        this.eventBus.register(this);
        this.gestureDetector.setDevice(this);
        this.timeWarpManager.setEventBus(this.eventBus);
    }

    @EventHandler
    public void postCompression(PostCompressionEvent postCompressionEvent) {
        StartTimeWarpEvent startTimeWarpEvent = new StartTimeWarpEvent(postCompressionEvent.getAfter());
        this.eventBus.post(startTimeWarpEvent);
        if (!startTimeWarpEvent.isCancelled()) {
            TimeWarpComparisonResults timeWarpComparisonResults = new TimeWarpComparisonResults();
            for (TimeSeriesComparison timeSeries : startTimeWarpEvent.getComparisons().getResults()) {
                timeWarpComparisonResults.addComparison(timeSeries);
            }
            this.timeWarpManager.addTimeWarpComp(timeWarpComparisonResults);
        }
    }

    public BuiltDevice setStartTimeWarp(boolean start) {
        Preconditions.checkNotNull(this.timeWarpManager);
        this.timeWarpManager.setStarted(start);
        if (start) {
            this.timeWarpManager.start();
        }
        return this;
    }

    public BuiltDevice setStartCompression(boolean start) {
        Preconditions.checkNotNull(this.compressionManager);
        this.compressionManager.setStarted(start);
        if (start) {
            this.compressionManager.start();
        }
        return this;
    }

    public BuiltDevice measuredSeries(TimeSeries timeSeries) {
        this.eventBus.post(new RecordedTimeSeriesEvent(timeSeries));
        if (this.compressionManager == null) {
            StartTimeWarpEvent startTimeWarpEvent = new StartTimeWarpEvent(timeSeries);
            this.eventBus.post(startTimeWarpEvent);
            if (!startTimeWarpEvent.isCancelled()) {
                this.timeWarpManager.addTimeWarpComp(startTimeWarpEvent.getComparisons());
            }
            return this;
        }
        this.compressionManager.addSeries(timeSeries);
        return this;
    }

    public BuiltDevice newMeasurement(float[] values, long time) {
        StartFilteringEvent startFilteringEvent = new StartFilteringEvent();
        this.eventBus.post(startFilteringEvent);
        float[] originalValues = values.clone();
        float[] filteredValues = values.clone();
        if (!startFilteringEvent.isCancelled()) {
            for (Filter filter : this.filters) {
                filteredValues = filter.filter(filteredValues);
            }
            PostFilterEvent postFilterEvent = new PostFilterEvent(originalValues, filteredValues);
            this.eventBus.post(postFilterEvent);
        }

        this.gestureDetector.newMeasurement(values, time);
        return this;
    }
}