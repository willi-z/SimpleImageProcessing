package org.analyser.transformers;

import javafx.scene.Group;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EllipseTransformer extends BaseTransformer{

    @Override
    public List<Mat> transform(List<Mat> mats, File image) {
        List<Mat> masks = super.transform(mats, image);

        List<Mat> results = new ArrayList<>();
        Mat hierarchy = new Mat();
        Scalar color = new Scalar(255, 0, 0);
        for (int i = 0; i < masks.size(); i++) {
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(masks.get(i), contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            double maxVal = 0;
            int maxValIdx = 0;
            for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++)
            {
                double contourArea = Imgproc.contourArea(contours.get(contourIdx));
                if (maxVal < contourArea)
                {
                    maxVal = contourArea;
                    maxValIdx = contourIdx;
                }
            }
            RotatedRect hull = Imgproc.fitEllipse(new MatOfPoint2f(contours.get(maxValIdx).toArray()));
            Mat img;
            if (i >= mats.size()){
                img = mats.get(0).clone();
            }
            else{
                img = mats.get(i).clone();
            }
            Imgproc.ellipse(img, hull, color);
            results.add(img);
            Map<String, Object> report = new HashMap<>();
            report.put("width", hull.size.width);
            report.put("height", hull.size.height);
            report.put("area", Math.PI * hull.size.height * hull.size.width);
            this.results.add(report);
        }
        return results;
    }
}
