package com.urbanfurniture.accounting.report;

import com.urbanfurniture.accounting.common.Money;
import com.urbanfurniture.accounting.identity.Party;
import com.urbanfurniture.accounting.ledger.JournalLineRepository;
import com.urbanfurniture.accounting.ledger.SystemAccount;
import com.urbanfurniture.accounting.security.CurrentUser;
import com.urbanfurniture.accounting.trade.DocumentType;
import com.urbanfurniture.accounting.trade.TradeDocument;
import com.urbanfurniture.accounting.trade.TradeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgingReportService {

    private final TradeDocumentRepository documentRepository;
    private final JournalLineRepository journalLineRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public ReportDtos.AgingReport aging(ReportDtos.AgingType type, LocalDate asOf) {
        Long bookId = currentUser.requireBookId();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        ReportDtos.AgingType effectiveType = type == null ? ReportDtos.AgingType.RECEIVABLE : type;

        DocumentType docType = effectiveType == ReportDtos.AgingType.RECEIVABLE
                ? DocumentType.INVOICE : DocumentType.BILL;
        SystemAccount control = effectiveType == ReportDtos.AgingType.RECEIVABLE
                ? SystemAccount.DEBTORS : SystemAccount.CREDITORS;

        Map<Long, Accumulator> byParty = new LinkedHashMap<>();
        Totals totals = new Totals();

        for (TradeDocument doc : documentRepository.findOpenAsOf(bookId, docType, date)) {
            BigDecimal due = doc.amountDue();
            if (!Money.isPositive(due)) {
                continue;
            }
            Party party = doc.getContact().getParty();
            Accumulator acc = byParty.computeIfAbsent(party.getId(),
                    id -> new Accumulator(id, party.getName()));

            long daysOverdue = daysOverdue(doc, date);
            AgingBucket bucket = AgingBucket.forDays(daysOverdue);

            acc.add(bucket, due);
            totals.add(bucket, due);
            acc.documents.add(new ReportDtos.AgingDocument(
                    doc.getId(), doc.getDocNo(), doc.getDocDate(), doc.getDueDate(),
                    daysOverdue, due, bucket.label()));
        }

        // The cross-check. Aging comes from documents because due dates
        // never reach a journal line, so its total is compared against the
        // control account it claims to describe.
        BigDecimal ledgerRaw = Money.nullSafe(
                journalLineRepository.balancesAsOf(bookId, date).stream()
                        .filter(r -> r.systemCode() == control)
                        .map(r -> r.naturalBalance())
                        .reduce(Money.ZERO, Money::add));

        BigDecimal difference = Money.subtract(totals.total, ledgerRaw);

        return new ReportDtos.AgingReport(
                date, effectiveType,
                byParty.values().stream().map(Accumulator::toRow).toList(),
                totals.current, totals.d1to30, totals.d31to60, totals.d61to90, totals.d90plus,
                totals.total, ledgerRaw, difference, Money.isZero(difference));
    }

    /**
     * Days past the due date at the report date.
     * <p>
     * Falls back to the document date when no due date was set, treating
     * it as payable on issue. Excluding such documents would drop real
     * money from the report and break the reconciliation.
     */
    private long daysOverdue(TradeDocument doc, LocalDate asOf) {
        LocalDate reference = doc.getDueDate() != null ? doc.getDueDate() : doc.getDocDate();
        return ChronoUnit.DAYS.between(reference, asOf);
    }

    private static final class Totals {
        BigDecimal current = Money.ZERO;
        BigDecimal d1to30 = Money.ZERO;
        BigDecimal d31to60 = Money.ZERO;
        BigDecimal d61to90 = Money.ZERO;
        BigDecimal d90plus = Money.ZERO;
        BigDecimal total = Money.ZERO;

        void add(AgingBucket bucket, BigDecimal amount) {
            switch (bucket) {
                case CURRENT -> current = Money.add(current, amount);
                case D1_30 -> d1to30 = Money.add(d1to30, amount);
                case D31_60 -> d31to60 = Money.add(d31to60, amount);
                case D61_90 -> d61to90 = Money.add(d61to90, amount);
                case D90_PLUS -> d90plus = Money.add(d90plus, amount);
            }
            total = Money.add(total, amount);
        }
    }

    private static final class Accumulator {
        private final Long partyId;
        private final String partyName;
        private final Totals totals = new Totals();
        private final List<ReportDtos.AgingDocument> documents = new ArrayList<>();

        Accumulator(Long partyId, String partyName) {
            this.partyId = partyId;
            this.partyName = partyName;
        }

        void add(AgingBucket bucket, BigDecimal amount) {
            totals.add(bucket, amount);
        }

        ReportDtos.AgingRow toRow() {
            return new ReportDtos.AgingRow(partyId, partyName,
                    totals.current, totals.d1to30, totals.d31to60, totals.d61to90, totals.d90plus,
                    totals.total, documents);
        }
    }
}
