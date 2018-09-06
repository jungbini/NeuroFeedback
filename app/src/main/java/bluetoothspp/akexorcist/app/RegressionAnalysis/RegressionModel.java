package bluetoothspp.akexorcist.app.RegressionAnalysis;

public abstract class RegressionModel {

    protected double[] xValues;     // x 좌표 값 배열(시간)
    protected double[] yValues;     // y 좌표 값 배열(alpha/theta 비율 값)
    protected boolean computed;     // 회귀분석이 완료되었는가?

    // 생성자 (double형 x, y 데이터 배열)
    public RegressionModel(double[] x, double[] y) {
        this.xValues = x;
        this.yValues = y;
        computed = false;
    }

    // x 좌표 값 배열 가져오기
    public double[] getXValues() {
        return this.xValues;
    }

    // y좌표 값 배열 가져오기
    public double[] getYValues() {
        return this.yValues;
    }

    // 회귀식의 Coefficient 값 가져오는 abstract 메소드
    public abstract double[] getCoefficients();

    // 회귀분석을 수행하는 abstract 메소드
    public abstract void compute();

    // 특정 x 좌표의 y 값을 가져오는 abstract 메소드
    public abstract double evaluateAt(double x);
}
