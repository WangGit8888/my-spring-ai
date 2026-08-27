package com.example.myspringai.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.ss.usermodel.PictureData;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

public class ExcelImageReader {

    public static void main(String[] args) throws Exception {
        String excelPath = "C:/test/人员信息.xlsx";

        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(excelPath))) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            // 获取 Sheet 上的所有图形（图片、形状等）
            if (sheet.getDrawingPatriarch() == null) {
                System.out.println("该 Sheet 没有图片");
                return;
            }

            List<XSSFShape> shapes = sheet.getDrawingPatriarch().getShapes();
            for (XSSFShape shape : shapes) {
                if (shape instanceof XSSFPicture) {
                    XSSFPicture picture = (XSSFPicture) shape;

                    // 1. 获取图片所在的单元格位置（行、列）
                    int rowIndex = picture.getClientAnchor().getRow1();   // 起始行
                    int colIndex = picture.getClientAnchor().getCol1();   // 起始列

                    // 2. 获取图片数据
                    PictureData pictureData = picture.getPictureData();
                    byte[] imageBytes = pictureData.getData();
                    String ext = pictureData.suggestFileExtension(); // 扩展名: jpg/png

                    // 3. 保存到本地（或者存入数据库、返回给前端）
                    String outputPath = String.format("C:/output/图片_行%d_列%d.%s", rowIndex, colIndex, ext);
                    try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                        fos.write(imageBytes);
                    }

                    System.out.printf("已提取图片: 行=%d, 列=%d, 大小=%d 字节, 格式=%s%n",
                            rowIndex, colIndex, imageBytes.length, ext);
                }
            }
        }
    }
}
