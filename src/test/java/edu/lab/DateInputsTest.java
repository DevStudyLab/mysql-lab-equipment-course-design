package edu.lab;

import com.github.lgooddatepicker.components.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import java.awt.event.ActionEvent;

class DateInputsTest {
    @Test void dateValuesAndInvalidInput()throws Exception {
        UiSmokeTest.edt(()->{
            Theme.install();DatePicker date=DateInputs.date("2028-02-29");assertEquals("2028-02-29",DateInputs.value(date));
            date.clear();assertEquals("",DateInputs.value(date));
            date.setText("2026-02-30");assertThrows(IllegalArgumentException.class,()->DateInputs.value(date));
            date.setDate(LocalDate.of(2026,11,4));assertEquals("2026-11-04",DateInputs.value(date));return null;
        });
    }
    @Test void timeRoundTripPartialAndEmpty()throws Exception {
        UiSmokeTest.edt(()->{
            Theme.install();DateInputs.DateTimeInput picker=DateInputs.dateTime("2026-11-03 08:09:17");assertEquals("2026-11-03 08:09:17",DateInputs.value(picker));
            JFormattedTextField field=((JSpinner.DateEditor)picker.getTimePicker().getEditor()).getTextField();
            field.select(6,8);field.replaceSelection("42");assertEquals("2026-11-03 08:09:42",DateInputs.value(picker));
            field.setText("23:45:59");assertEquals("2026-11-03 23:45:59",DateInputs.value(picker));
            field.setText("08:09:60");assertThrows(IllegalArgumentException.class,()->DateInputs.value(picker));
            field.setText("25:00:00");assertThrows(IllegalArgumentException.class,()->DateInputs.value(picker));
            picker.getDatePicker().clear();field.setText("");assertEquals("",DateInputs.value(picker));
            picker.getDatePicker().setDate(LocalDate.of(2026,11,3));assertThrows(IllegalArgumentException.class,()->DateInputs.value(picker));return null;
        });
    }
    @Test void spinnerAdjustsTheSelectedTimeSection()throws Exception {
        UiSmokeTest.edt(()->{
            Theme.install();DateInputs.DateTimeInput picker=DateInputs.dateTime("2026-11-03 08:09:17");JSpinner spinner=picker.getTimePicker();JFormattedTextField field=((JSpinner.DateEditor)spinner.getEditor()).getTextField();
            field.select(6,8);spinner.getActionMap().get("increment").actionPerformed(new ActionEvent(spinner,ActionEvent.ACTION_PERFORMED,"increment"));assertEquals("2026-11-03 08:09:18",DateInputs.value(picker));
            field.select(3,5);spinner.getActionMap().get("increment").actionPerformed(new ActionEvent(spinner,ActionEvent.ACTION_PERFORMED,"increment"));assertEquals("2026-11-03 08:10:18",DateInputs.value(picker));
            field.select(0,2);spinner.getActionMap().get("increment").actionPerformed(new ActionEvent(spinner,ActionEvent.ACTION_PERFORMED,"increment"));assertEquals("2026-11-03 09:10:18",DateInputs.value(picker));return null;
        });
    }
}
