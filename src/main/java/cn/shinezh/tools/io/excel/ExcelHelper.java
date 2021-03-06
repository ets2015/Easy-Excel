/**
 * 文件名：ExcelHelp.java
 * 版权所有： ShiNez
 * 文件版本：v1.0.0
 * 最后修改时间：2016-3-12
 */
package cn.shinezh.tools.io.excel;

import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel工具类
 *
 * @author ShiNez
 * @date 2016-3-12
 */
public final class ExcelHelper {


    /**
     * 导出
     *
     * @param srcList
     * @param headNames
     * @param dataFieldNames
     * @param tClass
     * @param <T>
     * @return
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws NoSuchFieldException
     */
    public static <T> HSSFWorkbook exportFromList(List<T> srcList, String[] headNames, String[] dataFieldNames, Class<T> tClass) throws IOException, IllegalAccessException, InstantiationException, NoSuchFieldException {
        return exportFromList(srcList, headNames, (obj -> {
            List<String> objectFieldValues = new ArrayList<>();
            Field field;
            for (int i = 0; i < dataFieldNames.length; i++) {
                field = getField(tClass, dataFieldNames[i]);
                String val = String.valueOf(field.get(obj) == null ? "" : field.get(obj));
                objectFieldValues.add(val);
            }
            return objectFieldValues;
        }));
    }

    /**
     * 导出
     * 通过反射获取该类或其父类属性值
     *
     * @param tClass
     * @param fieldName
     * @return
     */
    private static Field getField(Class tClass, String fieldName) {
        Field field = null;
        try {
            field = tClass.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Class sClass = tClass.getSuperclass();
            if (sClass == Object.class) {
                try {
                    throw new NoSuchFieldException(e.getMessage());
                } catch (NoSuchFieldException e1) {
                    e1.printStackTrace();
                }
            } else {
                return getField(sClass, fieldName);
            }
        }
        return field;
    }

    /**
     * 导出
     *
     * @param srcList     list集合，数据源
     * @param headNames   表头集合，定义生成的Excel的表头文字
     * @param excelExport 导出实现类
     * @return
     */
    public static <T> HSSFWorkbook exportFromList(List<T> srcList, String[] headNames, ExcelExport<T> excelExport) throws IOException, NoSuchFieldException, IllegalAccessException {
        HSSFWorkbook wb = new HSSFWorkbook();
        HSSFSheet sheet = wb.createSheet("sheet1");
        HSSFRow row0 = sheet.createRow(0);
        HSSFCellStyle cellStyle = wb.createCellStyle();
        //粗体
        HSSFFont font = wb.createFont();
        font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
        cellStyle.setFont(font);
        cellStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);
        //建立表头
        for (int i = 0; i < headNames.length; ++i) {
            HSSFCell cell = row0.createCell(i);
            cell.setCellValue(headNames[i]);
            cell.setCellStyle(cellStyle);
        }
        //填充数据
        HSSFRow row;
        HSSFCell cell;
        HSSFCellStyle dataCellStyle = wb.createCellStyle();
        for (int i = 0; i < srcList.size(); i++) {
            row = sheet.createRow(i + 1);
            T obj = srcList.get(i);
            List<String> columns = excelExport.getColumns(obj);
            for (int j = 0; j < columns.size(); ++j) {
                cell = row.createCell(j);
                cell.setCellValue(columns.get(j));
                cell.setCellStyle(dataCellStyle);
                sheet.setDefaultColumnStyle(j, cellStyle);
            }
        }
        return wb;
    }

    /**
     * 导出
     *
     * @param list
     * @param heads
     * @param fieldStr
     * @param tClass
     * @param fileName
     * @param response
     * @param <T>
     * @throws NoSuchFieldException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws IOException
     */
    public static <T> void exportFromListToStream(List<T> list, String[] heads, String[] fieldStr, Class<T> tClass, String fileName, HttpServletResponse response) throws NoSuchFieldException, InstantiationException, IllegalAccessException, IOException {
        HSSFWorkbook wb = exportFromList(list, heads, fieldStr, tClass);
        OutputStream out = response.getOutputStream();
        response.setHeader("content-disposition", "attachment;filename=" + URLEncoder.encode(fileName + ".xlsx", "UTF-8"));
        wb.write(out);
        out.close();
    }


    /**
     * 解析Excel导入
     *
     * @param <T>
     * @param fileName 文件名 如：test.xlsx
     * @param inputStream 输入流
     * @param tClass      类型
     * @param fieldsArr   字段名称数组
     * @return 返回对象的List集合
     */
    public static <T> List<T> importFromInputStream(String fileName,InputStream inputStream, Class<T> tClass, String... fieldsArr) throws Exception {
        return importFromInputStream(fileName,inputStream, columns -> {
            T t = tClass.newInstance();
            List<T> list = new ArrayList<>();
            Field field;
            for (int i = 0; i < fieldsArr.length; i++) {
                field = tClass.getDeclaredField(fieldsArr[i]);
                field.setAccessible(true);
                Object value = DataTypeConverter.parse(field.getType(), columns.get(i));
                field.set(t, value);
            }

            list.add(t);
            return list;
        });
    }


    /**
     * 解析Excel导入
     *
     * @param inputStream 输入流
     * @param excelImport 导入接口实现
     * @return 返回对象的List集合
     * @throws IOException
     */
    public static <T> List<T> importFromInputStream(String fileName,InputStream inputStream, ExcelImport<T> excelImport) throws Exception {
        Workbook wb = null;
        Sheet sheet;

        try {
            if(fileName.contains(".xlsx")){
                wb = new XSSFWorkbook(inputStream);
            }else{
                wb = new HSSFWorkbook(inputStream);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        sheet = wb.getSheetAt(0);
        List<T> list = new ArrayList<>();
        Row row;
        Cell cell;
        List<String> columns;
        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            row = sheet.getRow(i);
            columns = new ArrayList<>();
            boolean allFieldValidFlag = false;
            for (int j = row.getFirstCellNum(); j < row.getLastCellNum(); j++) {
                cell = row.getCell(j);
                String val = "";
                if (cell != null) {
                    cell.setCellType(HSSFCell.CELL_TYPE_STRING);
                    val = cell.getStringCellValue();
                }
                if (val != null && !"".equals(val) && !"".equals(val.trim())) {
                    allFieldValidFlag = true;
                }
                columns.add(val);
            }
            if (!allFieldValidFlag) {
                continue;
            }
            List<T> t = excelImport.getObjList(columns);
            list.addAll(t);
        }
        return list;
    }


}
