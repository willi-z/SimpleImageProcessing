package org.analyser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@FunctionalInterface
interface ImageProcess{
    void process(File Image);
}

public class App extends Application {
    private JSONObject config = new JSONObject();
    private Mat mat_main = null;
    private ImageView iv_main = new ImageView();
    private HBox filter_results = new HBox();
    private String current_image = "";
    private int nof_images = 0;
    private int index_image = 0;
    private int index_folder = 0;

    private Image mat_to_img(Mat mat){
        MatOfByte byteMat = new MatOfByte();
        Imgcodecs.imencode(".bmp", mat, byteMat);
        return new Image(new ByteArrayInputStream(byteMat.toArray()));
    }

    private void load_image(String image_path) {
        Mat mat_main = Imgcodecs.imread(image_path);
        Image image = mat_to_img(mat_main);
        iv_main.setImage(image);
    }

    private boolean is_image(File file){
        String filename = file.getName();
        filename = filename.toLowerCase();
        if(filename.contains(".tif")){
            return true;
        }
        if(filename.contains(".png")){
           return true;
        }
        if(filename.contains(".jpeg") || filename.contains(".jpg")){
            return true;
        }
        return false;
    }

    private void load_image_folder(File directory, ImageProcess proc){
        if (!directory.isDirectory()){
            return;
        }
        if(directory.getName().startsWith("debug")){
            return;
        }
        File[] directoryListing = directory.listFiles();
        if (directoryListing != null) {
            for (File file : directoryListing) {
                if (file.isDirectory()){
                    load_image_folder(file, proc);
                }
                if(!file.canRead()){
                    continue;
                }
                if (is_image(file)){
                    proc.process(file);
                }

            }
        }
    }

    private void load_config() {
        String config_path = "config.json";
        File file = new File(config_path);
        if (!file.exists()){
            return;
        }
        try {
            String content = FileUtils.readFileToString(file, "utf-8");
            config = new JSONObject(content);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reload_images(){
        nof_images = 0;
        current_image = "";
        JSONArray paths = (JSONArray)config.get("image_paths");
        if (paths == null){
            return;
        }
        ImageProcess proc = (image) -> {
            if (current_image.equals("")) {
                current_image = image.getAbsolutePath();
            }
            nof_images++;
        };
        for (Object path :paths){
            File directory  = new File((String)path);
            load_image_folder(directory, proc);
        }

        if (!current_image.equals("")){
            load_image(current_image);
        }
    }

    public void reload(){
        reload_images();
    }

    private void create_file(String filename){
        try {
            File f_config = new File(filename);
            f_config.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            String config_path = "config.json";
            create_file(config_path);
            FileWriter file = new FileWriter(config_path);
            file.write(config.toString(4));
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void display_result(String name, List<Mat> mats){
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
        filter_results.getChildren().add(new HBox(group));
    }

    private void store_result(File path, String posix, List<Mat> mats){
        String name = path.getName();
        int index = name.lastIndexOf(".");
        String debug = path.getParent() + "\\debug_java\\" + name.substring(0, index) + posix;
        File final_path = new File(debug);
        final_path.getParentFile().mkdirs();
        int counter = 0;
        for (Mat mat: mats) {
            String storage = debug + "_" + counter++ + ".png";
            Imgcodecs.imwrite(storage, mat);
        }
    }

    private List<Mat> process_converter(List<Mat> mats, JSONObject descr, File image){
        List<Mat> results= new ArrayList<>();
        String from = (String) descr.get("from");
        String to = (String) descr.get("to");

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

    private List<Mat> process_mask(List<Mat> mats, JSONObject descr, File image){
        List<Mat> results= new ArrayList<>();
        JSONArray ja_upper = descr.getJSONArray("upper");
        JSONArray ja_lower = descr.getJSONArray("lower");
        Scalar upper = new Scalar(ja_upper.getDouble(0), ja_upper.getDouble(1), ja_upper.getDouble(2));
        Scalar lower = new Scalar(ja_lower.getDouble(0), ja_lower.getDouble(1), ja_lower.getDouble(2));
        JSONArray reports = new JSONArray();
        for (Mat mat: mats) {
            Mat mask = new Mat();
            Core.inRange(mat, lower, upper, mask);
            results.add(mask);
            JSONObject report = new JSONObject();
            int pixels = 0;
            for (int y = 0; y < mask.cols(); y++) {
                for (int x = 0; x < mask.rows(); x++) {
                    if (mask.get(x, y)[0] == 255){
                        pixels++;
                    }
                }
            }
            report.put("pixels", pixels);
            reports.put(report);
        }
        descr.put("report", reports);
        return results;
    }

    private List<Mat> process_ellipse(List<Mat> mats, JSONObject descr, File image){
        List<Mat> results = new ArrayList<>();
        List<Mat> masks = process_filter(mats, descr, image);
        Mat hierarchy = new Mat();
        Scalar color = new Scalar(255, 0, 0);
        JSONArray reports = new JSONArray();
        for (int i = 0; i < masks.size(); i++) {
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(masks.get(i), contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            // sort contours
            // contours.sort((c1, c2) -> (int)(Imgproc.contourArea(c2) - Imgproc.contourArea(c1)));
            // RotatedRect hull = Imgproc.fitEllipse(new MatOfPoint2f(contours.get(0).toArray()));
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
            JSONObject report = new JSONObject();
            report.put("width", hull.size.width);
            report.put("height", hull.size.height);
            report.put("area", Math.PI * hull.size.height * hull.size.width);
            reports.put(report);
        }
        descr.put("report", reports);
        return results;
    }

    private List<Mat> process_optimisation(List<Mat> mats, JSONObject descr, File image){
        List<Mat> candidates =  process_filter(mats, descr, image);
        JSONArray filters = descr.getJSONArray("filters");
        JSONObject last_filter = filters.getJSONObject(filters.length() - 1);
        JSONArray last_filter_report = last_filter.getJSONArray("report");
        double optimal_value = Math.abs(last_filter_report.getJSONObject(0).getDouble("width") - last_filter_report.getJSONObject(0).getDouble("height"));
        int optimal_index = 0;
        for (int i = 1; i < last_filter_report.length(); i++) {
            JSONObject curr_report = last_filter_report.getJSONObject(i);
            double value = Math.abs(curr_report.getDouble("width") - curr_report.getDouble("height"));
            if (value < optimal_value){
                optimal_value = value;
                optimal_index = i;
            }
        }
        List<Mat> results = new ArrayList<>();
        results.add(candidates.get(optimal_index));
        return results;
    }



    private List<Mat> process_filter(List<Mat> mats, JSONObject descr, File image){
        JSONArray filters = (JSONArray) descr.get("filters");
        List<Mat> result = new ArrayList<>();

        for (Object filter_obj : filters){
            List<Mat> fil_result = null;
            JSONObject filter = (JSONObject) filter_obj;
            if (filter.has("type")){
                switch (filter.getString("type")) {
                    case "converter" -> fil_result = process_converter(mats, filter, image);
                    case "mask" -> fil_result = process_mask(mats, filter, image);
                    case "ellipse" -> fil_result = process_ellipse(mats, filter, image);
                    case "optimisation" -> fil_result = process_optimisation(mats, filter, image);
                }
            }
            else {
                fil_result = process_filter(mats, filter, image);
            }

            if (filter.has("name")){
                display_result(filter.getString("name"), fil_result);
            }

            if (filter.has("posix")){
                store_result(image, filter.getString("posix"), fil_result);
            }
            if (descr.has("process")){
                String processing = descr.getString("process");
                if (!processing.equals("parallel")){
                    mats = fil_result;
                    result = fil_result;
                }
                else{
                    for (Mat mat: fil_result){
                        result.add(mat);
                    }
                }
            }
            else{
                mats = fil_result;
                result = fil_result;
            }
        }
        return result;
    }


    public void process_image(File image){
        filter_results.getChildren().clear();
        Mat mat = Imgcodecs.imread(image.getAbsolutePath());
        Image main_image = mat_to_img(mat);
        iv_main.setImage(main_image);
        System.out.println(image.getAbsolutePath());
        List<Mat> mats = new ArrayList<>();
        mats.add(mat);
        process_filter(mats, new JSONObject(config.toString()) , image);
    }

    public void process_images(){
        JSONArray paths = (JSONArray)config.get("image_paths");
        if (paths == null){
            return;
        }
        for (Object path :paths){
            File directory  = new File((String)path);
            load_image_folder(directory, this::process_image);
        }
    }

    @Override
    public void start(Stage stage) {
        nu.pattern.OpenCV.loadLocally();

        Menu m_project = new Menu("_Project");

        MenuItem mi_open = new MenuItem("_Open");
        MenuItem mi_new = new MenuItem("_New");
        MenuItem mi_save = new MenuItem("_Save");
        mi_save.setOnAction(e -> {
                save();
        });
        MenuItem mi_settings = new MenuItem("_Settings");

        m_project.getItems().addAll(mi_open, mi_new, mi_save, mi_settings);

        Menu m_tools = new Menu("_Tools");
        MenuItem mi_images = new MenuItem("Add _Images");
        mi_images.setOnAction(
                e -> {
                    final DirectoryChooser directoryChooser =
                            new DirectoryChooser();
                    final File selectedDirectory =
                            directoryChooser.showDialog(stage);
                    if (selectedDirectory != null) {
                        String path = selectedDirectory.getAbsolutePath();
                        JSONArray paths = (JSONArray) config.get("image_paths");
                        if (paths == null){
                            paths = new JSONArray();
                            paths.put(path);
                            config.put("image_paths", paths);
                        }
                        else{
                            boolean exist =false;
                            for (int i = 0; i < paths.length(); i++) {
                                if (paths.getString(i).equals(path)) {
                                    exist = true;
                                    break;
                                }
                            }
                            if (!exist) {
                                paths.put(path);
                            }
                        }
                        reload();
                    }
                }
        );

        m_tools.getItems().addAll(mi_images);

        Menu m_analyse = new Menu("_Analyse");

        MenuItem mi_update = new MenuItem("_Update");
        mi_update.setOnAction(
                e->{
                    process_image(new File(current_image));
                }
        );

        MenuItem mi_run = new MenuItem("_Run");
        mi_run.setOnAction(
                e->{

                    process_images();
                }
        );

        m_analyse.getItems().addAll(mi_update, mi_run);

        MenuBar mb = new MenuBar();
        mb.getMenus().addAll(m_project, m_tools, m_analyse);

        iv_main.setFitHeight(455);
        iv_main.setFitWidth(500);
        iv_main.setPreserveRatio(true);
        VBox  group= new VBox();
        VBox renders = new VBox();
        filter_results.setMaxHeight(300);
        filter_results.setLayoutY(220);
        renders.getChildren().addAll(iv_main, filter_results);
        group.getChildren().addAll(mb, renders);
        Scene scene = new Scene(new StackPane(group), 600, 500);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            save();
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        load_config();
        reload();
    }

    public static void main(String[] args) {
        launch();
    }

}