package org.analyser.transformers;

import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;

public class BaseTransformer implements ITransformer {
    public List<BaseTransformer> transformers = new ArrayList<>();
    public String name;
    public String posix;
    public Map<String, Object> config = new HashMap<>();
    public List<Map<String, Object>> results = new ArrayList<>();
    public Map<String, String> store = new HashMap<>();
    public HBox visualizer;

    public void load(JSONObject descr){
        transformers.clear();
        config.clear();
        name = null;
        posix = null;

        for (String key: descr.keySet()){
            switch (key){
                case "filters":
                    JSONArray filters = descr.getJSONArray(key);
                    for (Object filter : filters){
                        transformers.add(TransformerBuilder.create((JSONObject) filter));
                    }
                    break;

                case "name":
                    name = descr.getString(key);
                    break;

                case "posix":
                    posix = descr.getString(key);
                    break;

                case "store":
                    JSONObject storage = descr.getJSONObject(key);
                    for (String store_key: storage.keySet()) {
                        store.put(store_key, storage.getString(store_key));
                    }
                    break;
                default:
                    config.put(key, descr.get(key));
            }
        }
    }

    public JSONObject toJson(){
        JSONObject result = new JSONObject();
        if (name != null){
            result.put("name", name);
        }
        if (posix != null){
            result.put("posix", posix);
        }

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }

        if (!transformers.isEmpty()) {
            JSONArray filters = new JSONArray();
            for (BaseTransformer transformer : transformers) {
                filters.put(transformer.toJson());
            }
            result.put("filters", filters);
        }
        if (store != null){
            result.put("store", store);
        }
        return result;
    }

    protected Image mat_to_img(Mat mat){
        MatOfByte byteMat = new MatOfByte();
        Imgcodecs.imencode(".bmp", mat, byteMat);
        return new Image(new ByteArrayInputStream(byteMat.toArray()));
    }

    protected void display_mats(List<Mat> mats){
        if (visualizer == null){
            return;
        }
        VBox images = new VBox();
        for (Mat mat: mats){
            ImageView imageView= new ImageView(mat_to_img(mat));
            imageView.setFitHeight(200);
            imageView.setFitWidth(200);
            imageView.setPreserveRatio(true);
            images.getChildren().add(imageView);
        }

        VBox group = new VBox();
        group.getChildren().addAll(new Label(name), images);
        visualizer.getChildren().add(new HBox(group));
    }

    protected void store_mats(List<Mat> mats, File image){
        String location = image.getAbsolutePath();
        int index = location.lastIndexOf(".");
        String debug = image.getParent() + "\\debug_java\\" + location.substring(0, index) + posix;
        File final_path = new File(debug);
        final_path.getParentFile().mkdirs();
        int counter = 0;
        for (Mat mat: mats) {
            String storage = debug + "_" + counter++ + ".png";
            Imgcodecs.imwrite(storage, mat);
        }
    }

    public void debug(List<Mat> mats, File image){
        if (name != null){
            display_mats(mats);
        }
        if (posix != null){
            store_mats(mats, image);
        }
    }

    public List<Mat> transform(List<Mat> mats, File image){
        results.clear();
        List<Mat> result = new ArrayList<>();
        for(BaseTransformer transformer : transformers){
            List<Mat> trans_result = transformer.transform(mats, image);
            transformer.debug(trans_result, image);
            Object process = config.get("process");
            if (process != null){
                if (process.equals("parallel")){
                    result.addAll(trans_result);
                    continue;
                }
            }
            mats = trans_result;
            result = trans_result;
        }
        return result;
    }

    public void store_result_in_excel(Sheet sheet, int row_number){
        for (BaseTransformer transformer: transformers){
            transformer.store_result_in_excel(sheet, row_number);
        }
        for (Map.Entry<String, String> element : store.entrySet())
        {
            String key = element.getKey();
            String title = element.getValue();

            Row title_row = sheet.getRow(0);
            Iterator<Cell> it = title_row.cellIterator();
            int colum = -1;
            int length = 0;
            while(it.hasNext()) {
                Cell cell = it.next();
                if (cell.getStringCellValue().equals(title)){
                    colum = cell.getAddress().getColumn();
                    System.out.println("store_result_in_excel");
                    System.out.println(title);
                    System.out.println(colum);
                    break;
                }
                length++;
            }

            if (colum == -1){
                title_row.createCell(length).setCellValue(title);
                colum = length;
            }
            for (Map<String, Object> result: results){
                Object value = result.get(key);
                if (value == null){
                    System.out.print("[ERROR] ");
                    System.out.println(key);
                    break;
                }
                sheet.getRow(row_number).createCell(colum).setCellValue((Double) value);
            }
        }


    }


    public void set_output(HBox visualizer){
        this.visualizer = visualizer;
        for (BaseTransformer transformer: transformers){
            transformer.set_output(visualizer);
        }
    }

    public void clear(){
        for (BaseTransformer transformer: transformers){
            transformer.clear();
        }
        transformers.clear();
        results.clear();
        store.clear();
        name = null;
        posix = null;
    }

    public void reset(){
        for (BaseTransformer transformer: transformers){
            transformer.reset();
        }
        results.clear();
    }

}
