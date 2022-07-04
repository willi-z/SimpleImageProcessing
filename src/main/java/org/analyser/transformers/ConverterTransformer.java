package org.analyser.transformers;

import javafx.scene.Group;
import org.json.JSONObject;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConverterTransformer extends BaseTransformer{

    @Override
    public List<Mat> transform(List<Mat> mats, File image) {
        List<Mat> results= new ArrayList<>();
        String from = (String) config.get("from");
        String to = (String) config.get("to");

        int code = 0;
        if (from.equals("BGR")) {
            if (to.equals("HSV")) {
                code = Imgproc.COLOR_BGR2HSV; // 40
            }
        }

        for (Mat mat: mats){
            Mat dist = new Mat();
            Imgproc.cvtColor(mat, dist, code);
            results.add(dist);
        }
        return results;
    }
}
