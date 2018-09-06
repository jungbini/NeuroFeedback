package bluetoothspp.akexorcist.app.RegressionAnalysis;

public class LinearRegressionModel extends RegressionModel {

    private double a;           // y 절편
    private double b;           // 기울기

    // 생성자 (double형 x, y 데이터 배열)
    public LinearRegressionModel(double[] x, double[] y) {
        super(x, y);
        a = b = 0;
    }

    // 회귀식에서 Coefficient 값 가져오기
    public double[] getCoefficients() {
        if (!computed)
            throw new IllegalStateException("Model has not yet computed");

        return new double[] {a, b};
    }

    // 회귀 분석 수행
    public void compute() {

        // x, y 데이터 배열의 크기가 2 미만이면 예외 발생
        if (xValues.length < 2 | yValues.length < 2)
            throw new IllegalArgumentException("Must have more than two values");

        // 기울기 b = cov[x,y] / var[x]
        b = MathUtils.covariance(xValues, yValues) / MathUtils.variance(xValues);

        // y 절편 a = ybar + b * xbar
        a = MathUtils.mean(yValues) - b * MathUtils.mean(xValues);

        // computed 플래그를 true로 세팅
        computed = true;
    }

    // x 좌표에서 y 값 구하기
    public double evaluateAt(double x) {
        if (!computed)
            throw new IllegalStateException("Model has not yet computed");

        return a + b * x;
    }

}
