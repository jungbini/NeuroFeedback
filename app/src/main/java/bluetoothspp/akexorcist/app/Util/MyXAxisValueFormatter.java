package bluetoothspp.akexorcist.app.Util;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

/**
 * Created by jungbini on 2018-03-31.
 */

public class MyXAxisValueFormatter implements IAxisValueFormatter {

    private String[] mValues;

    public MyXAxisValueFormatter(String [] values) {
        this.mValues = values;
    }

    public String getFormattedValue(float value, AxisBase axis) {
        return mValues[(int)value];
    }

}
