package org.analyser.transformers;

import org.opencv.core.Mat;

import java.io.File;
import java.util.List;

public interface ITransformer {
    List<Mat> transform(List<Mat> mats, File image);
}
