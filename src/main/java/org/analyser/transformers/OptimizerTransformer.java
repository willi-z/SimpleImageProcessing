package org.analyser.transformers;

import org.opencv.core.Mat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimizerTransformer extends BaseTransformer{

    @Override
    public List<Mat> transform(List<Mat> mats, File image) {
        List<Mat> candidates = super.transform(mats, image);
        BaseTransformer last_trans = transformers.get(transformers.size() - 1);
        List<Map<String, Object>> last_results = last_trans.results;
        double optimal_value = Math.abs((Double)last_results.get(0).get("width") - (Double)last_results.get(0).get("height"));
        int optimal_index = 0;
        for (int i = 1; i < last_results.size(); i++) {
            Map<String, Object> curr_res = last_results.get(i);
            double value = Math.abs((Double) curr_res.get("width") - (Double)curr_res.get("height"));
            if (value < optimal_value){
                optimal_value = value;
                optimal_index = i;
            }
        }
        List<Mat> results = new ArrayList<>();
        results.add(candidates.get(optimal_index));

        Map<String, Object> report = new HashMap<>();
        report.put("selected", optimal_index);
        this.results.add(report);
        return results;
    }
}
