package org.analyser.transformers;

import javafx.scene.Group;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaskTransformer extends BaseTransformer{

    @Override
    public List<Mat> transform(List<Mat> mats, File image) {
        this.results.clear();

        List<Mat> results= new ArrayList<>();
        JSONArray ja_upper = (JSONArray) config.get("upper");
        JSONArray ja_lower = (JSONArray) config.get("lower");
        Scalar upper = new Scalar(ja_upper.getDouble(0), ja_upper.getDouble(1), ja_upper.getDouble(2));
        Scalar lower = new Scalar(ja_lower.getDouble(0), ja_lower.getDouble(1), ja_lower.getDouble(2));

        for (Mat mat: mats) {
            Mat mask = new Mat();
            Core.inRange(mat, lower, upper, mask);
            results.add(mask);
            Map<String, Object> report = new HashMap<>();
            int pixels = 0;
            for (int y = 0; y < mask.cols(); y++) {
                for (int x = 0; x < mask.rows(); x++) {
                    if (mask.get(x, y)[0] == 255){
                        pixels++;
                    }
                }
            }
            report.put("pixels", pixels * 1.0);
            this.results.add(report);
        }
        return results;
    }
}
