package com.treppides.taskmanager.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class FeeAdjustmentRepository {

    private final JdbcTemplate jdbc;

    public FeeAdjustmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public List<Map<String, Object>> findByInvoiceCode(String invoiceCode, int year) {
        return jdbc.queryForList("""
            SELECT id, fee_type, invoice_code, manager_name, month_num, year,
                   amount, entity_name, country, entered_by, entered_at
            FROM   InvoiceAllocation.dbo.fee_adjustments
            WHERE  invoice_code = ? AND year = ?
            ORDER  BY month_num, fee_type
            """, invoiceCode, year);
    }

    public Map<String, Object> insert(String feeType, String invoiceCode, String managerName,
                                       int monthNum, int year, double amount,
                                       String entityName, String country, String enteredBy) {
        jdbc.update("""
            INSERT INTO InvoiceAllocation.dbo.fee_adjustments
                (fee_type, invoice_code, manager_name, month_num, year, amount, entity_name, country, entered_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, feeType, invoiceCode, managerName, monthNum, year, amount, entityName, country, enteredBy);

        // Return the inserted row
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT TOP 1 id, fee_type, invoice_code, manager_name, month_num, year,
                   amount, entity_name, country, entered_by, entered_at
            FROM   InvoiceAllocation.dbo.fee_adjustments
            WHERE  invoice_code = ? AND year = ? AND month_num = ? AND fee_type = ? AND amount = ?
            ORDER  BY id DESC
            """, invoiceCode, year, monthNum, feeType, amount);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public boolean deleteById(int id) {
        int affected = jdbc.update("""
            DELETE FROM InvoiceAllocation.dbo.fee_adjustments WHERE id = ?
            """, id);
        return affected > 0;
    }

    /** Sum fee adjustments by month and type for a given invoice_code + year. */
    public List<Map<String, Object>> findMonthlySums(String invoiceCode, int year) {
        return jdbc.queryForList("""
            SELECT month_num,
                   SUM(CASE WHEN fee_type = 'AUDIT' THEN amount ELSE 0 END) AS audit_fees,
                   SUM(CASE WHEN fee_type <> 'AUDIT' THEN amount ELSE 0 END) AS tax_fees
            FROM   InvoiceAllocation.dbo.fee_adjustments
            WHERE  invoice_code = ? AND year = ?
            GROUP  BY month_num
            """, invoiceCode, year);
    }

    /** Bulk fee adjustment sums for multiple invoice codes. */
    public List<Map<String, Object>> findBulkSums(List<String> invoiceCodes, int year) {
        if (invoiceCodes == null || invoiceCodes.isEmpty()) return List.of();
        String inClause = invoiceCodes.stream()
            .map(c -> "?")
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        Object[] params = new Object[invoiceCodes.size() + 1];
        for (int i = 0; i < invoiceCodes.size(); i++) params[i] = invoiceCodes.get(i);
        params[invoiceCodes.size()] = year;
        return jdbc.queryForList(
            "SELECT invoice_code, SUM(amount) AS total_fees " +
            "FROM InvoiceAllocation.dbo.fee_adjustments " +
            "WHERE invoice_code IN (" + inClause + ") AND year = ? " +
            "GROUP BY invoice_code",
            params);
    }
}
