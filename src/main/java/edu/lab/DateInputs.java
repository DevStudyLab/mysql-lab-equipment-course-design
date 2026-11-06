package edu.lab;

import com.github.lgooddatepicker.components.*;
import java.awt.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import javax.swing.*;

/** Binds existing calendar and Swing time-editor components to the database format. */
final class DateInputs {
    private DateInputs() {}
    private static DatePickerSettings dateSettings() {
        DatePickerSettings settings=new DatePickerSettings(Locale.CHINA);
        settings.setAllowEmptyDates(true);
        settings.setFormatForDatesCommonEra(DateTimeFormatter.ISO_LOCAL_DATE);
        settings.setFormatsForParsing(new ArrayList<>(Collections.singletonList(DateTimeFormatter.ISO_LOCAL_DATE)));
        settings.setFontValidDate(Theme.BODY);settings.setFontInvalidDate(Theme.BODY);
        return settings;
    }
    private static void styleDate(DatePicker picker){
        picker.setOpaque(false);
        Theme.field(picker.getComponentDateTextField());picker.getComponentDateTextField().setColumns(0);picker.getComponentDateTextField().setBorder(UIManager.getBorder("TextField.border"));
        JButton toggle=picker.getComponentToggleCalendarButton();toggle.setText("");toggle.setIcon(Theme.icon("calendar-event",Theme.MUTED,18));toggle.setPreferredSize(new Dimension(40,42));toggle.setToolTipText("选择日期");toggle.getAccessibleContext().setAccessibleName("选择日期");
    }
    static DatePicker date(String value) {
        DatePicker picker=new DatePicker(dateSettings());styleDate(picker);
        if(!value.isEmpty())picker.setDate(LocalDate.parse(value));
        return picker;
    }
    static DateTimeInput dateTime(String value) {return new DateTimeInput(value);}
    static final class DateTimeInput extends JPanel {
        private final DatePicker datePicker;
        private final JSpinner timePicker;
        DateTimeInput(String value){
            super(new BorderLayout(8,0));setOpaque(false);
            LocalDateTime initial=value.isEmpty()?LocalDateTime.now().withNano(0):LocalDateTime.parse(value,LabService.TIME);
            datePicker=date(value.isEmpty()?"":initial.toLocalDate().toString());
            Calendar clock=Calendar.getInstance();clock.set(2000,Calendar.JANUARY,1,initial.getHour(),initial.getMinute(),initial.getSecond());clock.set(Calendar.MILLISECOND,0);
            timePicker=new JSpinner(new SpinnerDateModel(clock.getTime(),null,null,Calendar.SECOND));
            JSpinner.DateEditor editor=new JSpinner.DateEditor(timePicker,"HH:mm:ss");editor.getFormat().setLenient(false);timePicker.setEditor(editor);
            editor.getTextField().setFocusLostBehavior(JFormattedTextField.PERSIST);editor.getTextField().setFont(Theme.BODY);
            editor.getTextField().setToolTipText("选中时、分或秒，输入数值或用上下键调整");
            timePicker.getAccessibleContext().setAccessibleName("时分秒");timePicker.setPreferredSize(new Dimension(154,42));
            add(datePicker,BorderLayout.CENTER);add(timePicker,BorderLayout.EAST);
            if(value.isEmpty())editor.getTextField().setText("");
        }
        DatePicker getDatePicker(){return datePicker;}
        JSpinner getTimePicker(){return timePicker;}
    }
    static String value(JComponent input) {
        if(input instanceof DateTimeInput) {
            DateTimeInput picker=(DateTimeInput)input;
            LabService.require(picker.datePicker.isTextFieldValid(),"日期无效，请使用日历选择正确日期");
            LocalDate date=picker.datePicker.getDate();JSpinner.DateEditor editor=(JSpinner.DateEditor)picker.timePicker.getEditor();
            boolean empty=editor.getTextField().getText().trim().isEmpty();
            if(date==null&&empty)return "";
            LabService.require(date!=null&&!empty,"请完整选择日期和时间");
            try{picker.timePicker.commitEdit();}catch(java.text.ParseException invalid){throw new IllegalArgumentException("时间无效，请输入00:00:00至23:59:59的时分秒");}
            return date+" "+editor.getFormat().format(picker.timePicker.getValue());
        }
        DatePicker picker=(DatePicker)input;
        LabService.require(picker.isTextFieldValid(),"日期无效，请使用日历选择正确日期");
        return picker.getDateStringOrEmptyString();
    }
}
