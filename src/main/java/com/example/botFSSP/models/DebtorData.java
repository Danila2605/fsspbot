package com.example.botFSSP.models;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

///ToDO поменять как будет нормальный файл

@Data
public class DebtorData {
    @ExcelProperty("№ п/п")
    private String id;
    @ExcelProperty("Статус")
    private String status;
    @ExcelProperty("Подразделение ОСП")
    private String unit;
    @ExcelProperty("Дата завершения ИП")
    private LocalDateTime dateEnd;
    @ExcelProperty("Регистрационный номер ИП")
    private String number;
    @ExcelProperty("Дата возбуждения")
    private LocalDateTime startDate;
    @ExcelProperty("Взыскатель")
    private String claimant;
    @ExcelProperty("Сумма долга")
    private Float amountDebt;
    @ExcelProperty("Остаток долга")
    private Float remainingDebt;
    @ExcelProperty("Тип должника")
    private String typeDebtor;

    @Override
    public String toString() {
        return  "№ п/п ='" + id + '\'' +
                ", Статус ='" + status + '\'' +
                ", Подразделение ОСП ='" + unit + '\'' +
                ", Дата завершения ИП =" + dateEnd +
                ", Регистрационный номер ИП ='" + number + '\'' +
                ", Дата возбуждения =" + startDate +
                ", Взыскатель ='" + claimant + '\'' +
                ", Сумма долга =" + amountDebt +
                ", Остаток долга =" + remainingDebt +
                ", Тип должника ='" + typeDebtor + '\'';
    }
}
