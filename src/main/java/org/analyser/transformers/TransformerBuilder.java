package org.analyser.transformers;


import org.json.JSONObject;


public class TransformerBuilder {

    public static BaseTransformer create(JSONObject build_descr){
        String type = "";
        if (build_descr.has("type")){
            type = build_descr.getString("type");
        }
        BaseTransformer result = null;
        switch (type) {
            case "converter" -> result = new ConverterTransformer();
            case "mask" -> result = new MaskTransformer();
            case "ellipse" -> result = new EllipseTransformer();
            case "optimisation" -> result = new OptimizerTransformer();
            default -> result = new BaseTransformer();
        }
        result.load(build_descr);
        return result;
    }

}
