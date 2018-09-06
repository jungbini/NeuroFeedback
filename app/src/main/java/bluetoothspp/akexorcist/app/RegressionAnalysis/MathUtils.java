package bluetoothspp.akexorcist.app.RegressionAnalysis;

/**
 * 다양한 수학 관련 함수 모음
 * 참고: https://ryanharrison.co.uk/2013/10/07/java-regression-library-linear-model.html
 */

public class MathUtils {

    // 두 데이터 배열의 공분산(covariance)를 구하기 위한 메소드 (double형 x, y 데이터 배열)
    public static double covariance(double[] x, double[] y) {
        double xmean = mean(x);
        double ymean = mean(y);

        double result = 0;

        for (int i = 0 ; i < x.length ; i++)
            result += (x[i] - xmean) * (y[i] - ymean);

        result /= x.length -1;

        return result;
    }

    // 데이터 배열의 평균 값을 구하기 위한 메소드 (double형 data 배열)
    public static double mean(double[] data) {
        double sum = 0;

        for (int i = 0 ; i < data.length ; i++)
            sum += data[i];

        return sum / data.length;
    }

    // 데이터 배열의 분산을 구하기 위한 메소드 (double형 data 배열)
    public static double variance(double[] data) {
        double mean = mean(data);

        double sumOfSquaredDeviations = 0;

        for (int i = 0 ; i < data.length ; i++)
            sumOfSquaredDeviations += Math.pow(data[i] - mean, 2);

        return sumOfSquaredDeviations / (data.length - 1);
    }
}
