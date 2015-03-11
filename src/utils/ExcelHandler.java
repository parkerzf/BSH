package utils;
import jxl.*;

import java.io.*;


public class ExcelHandler {
	Workbook rwb = null;
	public ExcelHandler(String filePath) {
		try {
			InputStream is = new FileInputStream(filePath);
			rwb = Workbook.getWorkbook(is);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * close the work book
	 */
	public void close(){
		rwb.close();
	}

	/**
	 * set the work book
	 */
	public void setWorkbook(String filePath){
		if(rwb!=null)
			rwb.close();
		try {
			InputStream is = new FileInputStream(filePath);
			rwb = Workbook.getWorkbook(is);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * read a range of cells with type double from xsl
	 * @param sheetName: the name of the sheet to read from 
	 * @param colStart, colEnd, the column read range, start from 0 
	 * @param rowStart, rowEnd, the row read range, start from 0
	 */
	public double[][] xlsread(String sheetName, int colStart, int colEnd, int rowStart,int rowEnd){
		Sheet st = rwb.getSheet(sheetName);
		double[][] cells = new double[colEnd-colStart +1][rowEnd - rowStart +1];
		for(int i = colStart; i<= colEnd; i++){
			for(int j = rowStart; j<= rowEnd; j++)
				cells[i- colStart][j - rowStart] = parseDouble(st,i,j); 
		}
		return cells;
	}

	public double[] xlsread(String sheetName, int col, int rowStart,int rowEnd){
		Sheet st = rwb.getSheet(sheetName);
		double[] cells = new double[rowEnd - rowStart +1];
		for(int i = rowStart; i<= rowEnd; i++)
			cells[i - rowStart] = parseDouble(st,col,i); 
		return cells;
	}

	public double xlsread(String sheetName, int col, int row){
		Sheet st = rwb.getSheet(sheetName); 
		return parseDouble(st, col, row) ;
	}



	private double parseDouble(Sheet st, int colIndex, int rowIndex) {
		Cell c00 = st.getCell(colIndex, rowIndex);
		NumberCell labelc00 = (NumberCell) c00;
		return labelc00.getValue();
	}

}
