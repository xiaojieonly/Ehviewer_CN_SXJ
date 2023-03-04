package com.hippo.ehviewer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.R;

import java.util.Calendar;

public class JumpDateSelector extends LinearLayout implements DatePicker.OnDateChangedListener{
    private MyRadioGroup mRadioGroup;
    private RadioButton mSelectRadio;
    private TextView foundMessage;
    private OnTimeSelectedListener onTimeSelectedListener;
    private Button pickDateButton;
    private Button gotoJumpButton;
    private DatePicker datePicker;
    private static final int DATE_PICKER_TYPE = 2;
    private static final int DATE_NODE_TYPE = 1;

    private int dateJumpType = 1;

    private int dayOfMonthSelected;
    private int monthOfYearSelected;
    private int yearSelected;

    private ColorStateList radioButtonOriginColor = null;
    public JumpDateSelector(Context context) {
        super(context);
        init(context);
    }

    public JumpDateSelector(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public JumpDateSelector(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public JumpDateSelector(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.gallery_list_jump_selector,this);
        datePicker = findViewById(R.id.date_picker_view);
        Calendar calendar = Calendar.getInstance();
        yearSelected = calendar.get(Calendar.YEAR);
        monthOfYearSelected = calendar.get(Calendar.MONTH);
        dayOfMonthSelected = calendar.get(Calendar.DAY_OF_MONTH);
        datePicker.init(yearSelected, monthOfYearSelected, dayOfMonthSelected,this);
        datePicker.setMaxDate(calendar.getTimeInMillis());

        Calendar minDate;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            minDate  = new Calendar.Builder().setDate(2007,3,20).build();
        }else {
            minDate = Calendar.getInstance();
            minDate.set(2007,3,20);
        }
        datePicker.setMinDate(minDate.getTimeInMillis());
        pickDateButton = findViewById(R.id.picker_date);
        gotoJumpButton = findViewById(R.id.goto_jump);
        pickDateButton.setOnClickListener(this::onClickPickerDateButton);
        gotoJumpButton.setOnClickListener(this::buildJumpParamAndGoto);
        mRadioGroup =  findViewById(R.id.jump_date_picker_group);
        mRadioGroup.setOnCheckedChangeListener(this::onSelectChange);
        foundMessage = findViewById(R.id.found_message);
    }

    private void buildJumpParamAndGoto(View view) {
        String urlAppend;
        if (dateJumpType == DATE_PICKER_TYPE){
            urlAppend = datePickerAppendBuild();
        }else{
            urlAppend = nodePickerAppendBuild();
        }
        onTimeSelectedListener.onTimeSelected(urlAppend);
    }

    private String datePickerAppendBuild(){
        return  "seek="+yearSelected+"-"+monthOfYearSelected+"-"+dayOfMonthSelected;
    }

    @SuppressLint("NonConstantResourceId")
    private String nodePickerAppendBuild(){
        if (mSelectRadio==null){
            return "";
        }
        final int selectId = mSelectRadio.getId();
        String param;
        switch (selectId){
            default:
            case R.id.jump_1d:
                param = "1d";
                break;
            case R.id.jump_3d:
                param = "3d";
                break;
            case R.id.jump_1w:
                param = "1w";
                break;
            case R.id.jump_2w:
                param = "2w";
                break;
            case R.id.jump_1m:
                param = "1m";
                break;
            case R.id.jump_6m:
                param = "6m";
                break;
            case R.id.jump_1y:
                param = "1y";
                break;
            case R.id.jump_2y:
                param = "2y";
                break;
        }
        return "jump="+param;
    }

    private void onClickPickerDateButton(View view) {
        if (dateJumpType == DATE_PICKER_TYPE){
            datePicker.setVisibility(GONE);
            mRadioGroup.setVisibility(VISIBLE);
            dateJumpType = DATE_NODE_TYPE;
            pickDateButton.setText(R.string.gallery_list_select_jump_date);
        }else{
            datePicker.setVisibility(VISIBLE);
            mRadioGroup.setVisibility(GONE);
            dateJumpType = DATE_PICKER_TYPE;
            pickDateButton.setText(R.string.gallery_list_select_jump_node);
        }
    }

    private void onSelectChange(RadioGroup radioGroup, int i) {
        if (i==-1){
            return;
        }
        if (radioButtonOriginColor==null){
            mSelectRadio = radioGroup.findViewById(i);
            radioButtonOriginColor = mSelectRadio.getTextColors();
        }else{
            mSelectRadio.setTextColor(radioButtonOriginColor);
            mSelectRadio = radioGroup.findViewById(i);
        }

        mSelectRadio.setTextColor(mSelectRadio.getHighlightColor());
    }

    public void setFoundMessage(String message){
        if (message == null || message.isEmpty()){
            foundMessage.setVisibility(GONE);
        }else{
            foundMessage.setVisibility(VISIBLE);
            foundMessage.setText(getResources().getString(R.string.gallery_list_time_jump_dialog_found_message,message));
        }
    }

    public void setOnTimeSelectedListener(OnTimeSelectedListener listener) {
        onTimeSelectedListener = listener;
    }

    @Override
    public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        yearSelected =year;
        monthOfYearSelected = monthOfYear+1;
        dayOfMonthSelected = dayOfMonth;
    }

    public interface OnTimeSelectedListener{
        void onTimeSelected(String urlAppend);
    }

}
