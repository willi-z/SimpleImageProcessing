package org.analyser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
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
import org.analyser.transformers.BaseTransformer;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
interface ImageProcess{
    void process(File Image);
}

public class App extends Application {
    private final ImageView iv_main = new ImageView();
    private final HBox filter_results = new HBox();

    private final BaseTransformer transformer = new BaseTransformer();

    private JSONObject config = new JSONObject();
    private String current_image = "";
    private Workbook workbook = null;
    private int row_number = 0;


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

        transformer.load(config);
        transformer.set_output(filter_results);
    }

    private void reload_images(){
        current_image = "";
        JSONArray paths = (JSONArray)config.get("image_paths");
        if (paths == null){
            return;
        }
        ImageProcess proc = (image) -> {
            if (current_image.equals("")) {
                current_image = image.getAbsolutePath();
            }
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

    public void process_image(File image){
        filter_results.getChildren().clear();
        Mat mat = Imgcodecs.imread(image.getAbsolutePath());
        Image main_image = mat_to_img(mat);
        iv_main.setImage(main_image);
        System.out.println(image.getAbsolutePath());
        List<Mat> mats = new ArrayList<>();
        mats.add(mat);
        transformer.transform(mats, image);
    }

    private void process_image_with_excel(File image){
        process_image(image);
        Sheet sheet = workbook.getSheet("Measurements");
        Row row = sheet.createRow(row_number);
        row.createCell(0).setCellValue(image.getName());
        transformer.store_result_in_excel(sheet, row_number);
        row_number++;
    }

    private void setup_excel(){
        workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Measurements");
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 5000);
        sheet.setColumnWidth(3, 5000);
        sheet.setColumnWidth(4, 5000);
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("title");
        // row.createCell(1).setCellValue("percentage [%]");
        // row.createCell(2).setCellValue("area of gel [px^2]");
        // row.createCell(3).setCellValue("area of impurities [px^2]");
        // row.createCell(4).setCellValue("areas of impurities [px^2]");
        row_number = 1;
    }

    private void save_excel(File directory){
        String fileLocation = directory.getAbsolutePath() + "\\" + "d_measurements.xlsx";

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(fileLocation);
            workbook.write(outputStream);
            workbook.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void process_images(){
        JSONArray paths = (JSONArray)config.get("image_paths");
        if (paths == null){
            return;
        }
        for (Object path :paths){
            setup_excel();
            File directory  = new File((String)path);
            load_image_folder(directory, this::process_image_with_excel);
            save_excel(directory);
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