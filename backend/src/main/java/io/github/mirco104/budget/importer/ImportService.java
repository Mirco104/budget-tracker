package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.bankaccount.AccountRole;
import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.bankaccount.BankAccountSource;
import io.github.mirco104.budget.bankaccount.BankAccountSourceRepository;
import io.github.mirco104.budget.bankaccount.Iban;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.importer.BankCsv.BankFile;
import io.github.mirco104.budget.importer.BankCsv.Line;
import io.github.mirco104.budget.importer.ImportPreviewResponse.DuplicateOf;
import io.github.mirco104.budget.importer.ImportPreviewResponse.Entry;
import io.github.mirco104.budget.importer.ImportPreviewResponse.Replaces;
import io.github.mirco104.budget.plan.ItemTermRepository;
import io.github.mirco104.budget.plan.PlanService;
import io.github.mirco104.budget.plan.PlannedItem;
import io.github.mirco104.budget.plan.PlannedItemRepository;
import io.github.mirco104.budget.plan.TermMatcher;
import io.github.mirco104.budget.plan.TermMatcher.Planned;
import io.github.mirco104.budget.plan.TermSchedule;
import io.github.mirco104.budget.plan.TermRef;
import io.github.mirco104.budget.plan.TermService;
import io.github.mirco104.budget.importer.ImportPreviewResponse.Source;
import io.github.mirco104.budget.importer.ImportPreviewResponse.Status;
import io.github.mirco104.budget.suggestion.BookingSuggestionService;
import io.github.mirco104.budget.suggestion.CategorySuggester;
import io.github.mirco104.budget.suggestion.CategorySuggester.Suggestion;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CSV import of the signed-in user (R15, R26 to R30, US33, US34). */
@Service
public class ImportService {

    /** R30: the largest file and the most lines. */
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_LINES = 5000;
    /** Issue #119: the longest purpose the details of a row show. */
    static final int MAX_PURPOSE = 500;

    private final TransactionRepository transactionRepository;
    private final ImportBatchRepository batchRepository;
    private final BankAccountService bankAccountService;
    private final BankAccountSourceRepository sourceRepository;
    private final CategoryService categoryService;
    private final BookingSuggestionService suggestionService;
    private final TermService termService;
    private final PlanService planService;
    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ImportService(
            TransactionRepository transactionRepository, ImportBatchRepository batchRepository,
            BankAccountService bankAccountService, BankAccountSourceRepository sourceRepository,
            CategoryService categoryService, BookingSuggestionService suggestionService,
            TermService termService, PlanService planService, PlannedItemRepository itemRepository,
            ItemTermRepository termRepository, CurrentUser currentUser,
            Clock clock, io.github.mirco104.budget.plan.PlanSuggestionService planSuggestionService,
            io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository) {
        this.planSuggestionService = planSuggestionService;
        this.statementRepository = statementRepository;
        this.termService = termService;
        this.planService = planService;
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.bankAccountService = bankAccountService;
        this.sourceRepository = sourceRepository;
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    private final io.github.mirco104.budget.plan.PlanSuggestionService planSuggestionService;
    private final io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository;

    /** A new row of the file with its fingerprint and the values the preview shows. */
    private record Candidate(int index, Line line, String source, String fingerprint, String description,
            String counterparty, String bankCategory, String text) {
    }

    // --- preview ---

    /** Reads the file and shows what an import would do; stores nothing (decision 44). */
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            throw new InvalidFieldException("file", "Die Datei darf höchstens 2 MB groß sein.");
        }
        BankFile file;
        try {
            file = BankCsv.read(bytes);
        } catch (BankCsv.UnknownFormatException e) {
            throw new InvalidFieldException("file", e.getMessage());
        }
        if (file.lines().size() > MAX_LINES) {
            throw new InvalidFieldException("file", "Die Datei darf höchstens " + MAX_LINES + " Zeilen haben.");
        }
        BankProfile profile = file.profile();
        User owner = currentUser.get();
        List<BankAccount> accounts = bankAccountService.findAllOrdered();

        // Sources in the order they appear, with the account they go to so far
        Map<String, Long> remembered = sourceRepository.findByOwner(owner).stream()
                .collect(Collectors.toMap(BankAccountSource::getSourceKey, s -> s.getAccount().getId()));
        // R30: rows the rules leave out (pending, another currency) count as skipped
        Map<Integer, String> ruleSkips = new HashMap<>();
        for (Line line : file.lines()) {
            if (line.row() != null && ImportRules.skipReason(line.row()) != null) {
                ruleSkips.put(line.number(), ImportRules.skipReason(line.row()));
            }
        }
        Map<String, Source> sources = new LinkedHashMap<>();
        for (Line line : file.lines()) {
            if (line.row() != null && !ruleSkips.containsKey(line.number())) {
                String key = ImportRules.sourceKey(profile, line.row().accountName());
                sources.computeIfAbsent(key, k -> source(profile, line.row().accountName(), k, remembered, accounts));
            }
        }
        // Decision 92: the balance the file delivers for a source
        for (BankCsv.Balance balance : file.balances()) {
            String key = ImportRules.sourceKey(profile, balance.accountName());
            sources.computeIfPresent(key, (k, source) -> source.withBalance(balance.date(), balance.amount()));
        }

        // Fingerprints, with the number of equal rows (R30)
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Integer> equalRows = new HashMap<>();
        for (Line line : file.lines()) {
            BankTransaction row = line.row();
            if (row == null || ruleSkips.containsKey(line.number())) {
                continue;
            }
            int number = equalRows.merge(ImportRules.sameRowKey(profile, row), 1, Integer::sum);
            // Decision 95: a PayPal direct debit is named after the merchant ("Prezi Inc · PayPal")
            String merchant = ImportRules.payPalMerchant(row.counterpartyName(), row.counterpartyBic(),
                    row.remittanceInformation());
            String counterparty = cut(merchant != null ? merchant : row.counterpartyName());
            String description = merchant != null
                    ? ImportRules.describe(merchant, "PayPal", row.bankTransactionCode())
                    : ImportRules.describe(row.counterpartyName(), ImportRules.cleanPurpose(row.remittanceInformation()),
                            row.bankTransactionCode());
            candidates.add(new Candidate(candidates.size(), line, ImportRules.sourceKey(profile, row.accountName()),
                    ImportRules.fingerprint(profile, row, number), description, counterparty, cut(row.bankCategory()),
                    counterparty != null ? counterparty : description));
        }
        Set<String> imported = candidates.isEmpty() ? Set.of()
                : new HashSet<>(transactionRepository.findImportedFingerprints(owner,
                        candidates.stream().map(Candidate::fingerprint).toList()));
        List<Candidate> fresh = candidates.stream().filter(c -> !imported.contains(c.fingerprint())).toList();

        // R27 and R30 against the stored single transactions around the dates of the file
        // A booking that fulfils a term (P5) is no counterpart of a transfer, but still a possible duplicate
        List<Transaction> stored = storedAround(owner, fresh);
        Set<Long> replacing = termService.linkedIds(stored.stream().map(Transaction::getId).toList());
        // A stored transfer without its other side is a counterpart too (a transfer booked by hand, R27)
        List<Transaction> storedTransfers = transfersAround(owner, fresh);
        Map<Long, Transaction> storedById = new HashMap<>();
        stored.forEach(t -> storedById.put(t.getId(), t));
        storedTransfers.forEach(t -> storedById.put(t.getId(), t));
        // Decision 95: the own accounts with a PayPal source, for the PayPal pair (R27)
        Set<Long> payPalAccounts = sourceRepository.findByOwner(owner).stream()
                .filter(s -> s.getSourceKey().startsWith(BankProfile.PAYPAL.name() + ":"))
                .map(s -> s.getAccount().getId())
                .collect(Collectors.toSet());
        List<ImportMatcher.Existing> existing = stored.stream().map(t -> existing(t, payPalAccounts)).toList();
        List<ImportMatcher.Existing> transferPeers = existing.stream()
                .filter(e -> !replacing.contains(e.id()))
                .toList();
        List<ImportMatcher.Row> matcherRows = fresh.stream()
                .map(c -> matcherRow(c, profile, sources.get(c.source()).accountId(), payPalAccounts))
                .toList();
        List<ImportMatcher.OwnAccount> ownAccounts = accounts.stream()
                .map(a -> new ImportMatcher.OwnAccount(a.getId(), a.getIban()))
                .toList();
        Map<Integer, ImportMatcher.Transfer> transfers = ImportMatcher.transfers(matcherRows, transferPeers,
                storedTransfers.stream().map(t -> existing(t, payPalAccounts)).toList(), ownAccounts);
        Map<Integer, Long> duplicates = ImportMatcher.possibleDuplicates(matcherRows, existing);
        BookingSuggestionService.Suggester suggester = suggestionService.suggester();

        Map<Integer, Suggestion> suggestions = new HashMap<>();
        for (Candidate c : fresh) {
            if (!transfers.containsKey(c.index())) {
                suggester.suggest(c.text(), c.line().row().amount().signum() > 0, c.bankCategory(), profile.kindHints())
                        .ifPresent(suggestion -> suggestions.put(c.index(), suggestion));
            }
        }
        Set<Integer> savingRows = savingRows(fresh, transfers, candidates, sources, accounts, storedById);
        Map<Long, Long> autoEntries = new HashMap<>();
        Map<Integer, Planned> matches = occurrenceMatches(fresh, transfers, savingRows, suggestions, autoEntries);

        Map<Integer, Entry> entries = new HashMap<>();
        for (Candidate c : candidates) {
            if (imported.contains(c.fingerprint())) {
                entries.put(c.line().number(),
                        entry(c, Status.DUPLICATE, "schon importiert", null, null, null, null, null, null, false));
                continue;
            }
            ImportMatcher.Transfer transfer = transfers.get(c.index());
            Planned match = matches.get(c.index());
            Long autoEntry = match == null ? null : autoEntries.get(autoKey(match));
            Long duplicateId = duplicates.get(c.index());
            // P6: the automatic entry the row replaces is no duplicate
            if (duplicateId != null && duplicateId.equals(autoEntry)) {
                duplicateId = null;
            }
            Status status = duplicateId != null ? Status.POSSIBLE_DUPLICATE : Status.NEW;
            // P5: a matching term goes before the suggestion, with the category of its item; a saving item has none
            Suggestion suggestion = match == null ? suggestions.get(c.index())
                    : match.categoryId() == null ? null : new Suggestion(match.categoryId(), true);
            // Decision 67: a transfer to a savings or depot account, or a row that fulfils a saving item, is saving
            boolean saving = match != null ? match.categoryId() == null : savingRows.contains(c.index());
            DuplicateOf duplicateOf = duplicateId == null ? null : duplicateOf(storedById.get(duplicateId));
            entries.put(c.line().number(), entry(c, status, null, suggestion, transfer,
                    transferPeer(transfer, candidates, storedById, sources), match, autoEntry, duplicateOf, saving));
        }
        List<Entry> ordered = file.lines().stream()
                .map(line -> line.row() == null ? Entry.skipped(line.number(), line.skipReason())
                        : ruleSkips.containsKey(line.number()) ? Entry.skipped(line.number(), ruleSkips.get(line.number()))
                        : entries.get(line.number()))
                .toList();

        int duplicateCount = (int) ordered.stream().filter(e -> e.status() == Status.DUPLICATE).count();
        int skipped = (int) ordered.stream().filter(e -> e.status() == Status.SKIPPED).count();
        List<ImportPreviewResponse.SeriesSuggestion> series = seriesSuggestions(fresh, transfers, savingRows, matches, suggestions);

        // Decision 91: what the setup wizard suggests from the file: budgets from the expenses that are no series, no
        // term, no transfer and no saving; the income from all income rows that are no transfer
        Set<Integer> seriesLines = series.stream().flatMap(s -> s.lines().stream()).collect(Collectors.toSet());
        List<io.github.mirco104.budget.plan.PlanSuggestionService.FileRow> counted = new ArrayList<>();
        List<io.github.mirco104.budget.plan.PlanSuggestionService.FileRow> variable = new ArrayList<>();
        for (Candidate c : fresh) {
            if (transfers.containsKey(c.index()) || savingRows.contains(c.index())) {
                continue;
            }
            Suggestion suggestion = suggestions.get(c.index());
            var row = new io.github.mirco104.budget.plan.PlanSuggestionService.FileRow(c.line().row().bookingDate(),
                    c.line().row().amount(), suggestion == null ? null : suggestion.categoryId(), c.bankCategory());
            counted.add(row);
            if (!seriesLines.contains(c.line().number()) && !matches.containsKey(c.index())) {
                variable.add(row);
            }
        }
        var income = planSuggestionService.income(counted);
        return new ImportPreviewResponse(profile, profile.label(), ordered.size(), ordered.size() - duplicateCount - skipped,
                duplicateCount, skipped, List.copyOf(sources.values()), ordered, series,
                planSuggestionService.budgetSuggestions(variable),
                new ImportPreviewResponse.Income(income.average(), income.minimum(), income.months()));
    }

    // Decision 92: stores the balance of the file for its day (replacing one of the same day), never over a newer one
    private void takeBalance(User owner, BankAccount account, java.time.LocalDate date, java.math.BigDecimal amount) {
        boolean newer = statementRepository.findByOwnerOrderByDateAsc(owner).stream()
                .anyMatch(s -> s.getAccount().getId().equals(account.getId()) && s.getDate().isAfter(date));
        if (newer) {
            return;
        }
        var statement = statementRepository.findByAccountAndDate(account, date)
                .orElseGet(() -> new io.github.mirco104.budget.bankaccount.BankStatement(owner, account, date));
        statement.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        statementRepository.save(statement);
    }

    /** R33: only C24 and the Sparkasse deliver a category; the others only the kind of booking. */
    private static final Set<BankProfile> BANK_CATEGORY_PROFILES = Set.of(BankProfile.C24, BankProfile.SPARKASSE);

    /**
     * R33: the category of the bank of a row, null if the bank has none; the Sparkasse puts the kind of booking there
     * when its export has no column Kategorie (R30), which is no category.
     */
    private static String ownBankCategory(BankProfile profile, Candidate c) {
        String bankCategory = c.bankCategory();
        if (!BANK_CATEGORY_PROFILES.contains(profile) || bankCategory == null || bankCategory.isBlank()
                || bankCategory.equals(c.line().row().bankTransactionCode())) {
            return null;
        }
        return bankCategory;
    }

    private static Source source(
            BankProfile profile, String name, String key, Map<String, Long> remembered, List<BankAccount> accounts) {
        String suggestedName = ImportRules.accountName(profile, name);
        Long accountId = remembered.get(key);
        if (accountId == null) {
            accountId = accounts.stream()
                    .filter(a -> a.getName().equalsIgnoreCase(suggestedName))
                    .map(BankAccount::getId)
                    .findFirst()
                    .orElse(null);
        }
        // AP 26h: a source that is an IBAN (Sparkasse, VR-Bank) goes to the own account with that IBAN
        String iban = Iban.normalize(name);
        if (accountId == null && iban != null) {
            accountId = accounts.stream()
                    .filter(a -> iban.equals(a.getIban()))
                    .map(BankAccount::getId)
                    .findFirst()
                    .orElse(null);
        }
        return new Source(key, name == null || name.isBlank() ? profile.label() : name, accountId, suggestedName, null,
                null);
    }

    private List<Transaction> storedAround(User owner, List<Candidate> fresh) {
        if (fresh.isEmpty()) {
            return List.of();
        }
        LocalDate from = fresh.stream().map(c -> c.line().row().bookingDate()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = fresh.stream().map(c -> c.line().row().bookingDate()).max(Comparator.naturalOrder()).orElseThrow();
        return transactionRepository.findCountedBetween(owner, from.minusDays(ImportMatcher.MAX_TRANSFER_DAYS),
                to.plusDays(ImportMatcher.MAX_TRANSFER_DAYS));
    }

    // R27: the stored transfers twice as far around, so one at the edge still finds its stored other side
    private List<Transaction> transfersAround(User owner, List<Candidate> fresh) {
        if (fresh.isEmpty()) {
            return List.of();
        }
        LocalDate from = fresh.stream().map(c -> c.line().row().bookingDate()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = fresh.stream().map(c -> c.line().row().bookingDate()).max(Comparator.naturalOrder()).orElseThrow();
        return transactionRepository.findTransfersBetween(owner, from.minusDays(2L * ImportMatcher.MAX_TRANSFER_DAYS),
                to.plusDays(2L * ImportMatcher.MAX_TRANSFER_DAYS));
    }

    // Decision 95: a booking on a PayPal account is that side of a PayPal pair, one that names PayPal the other
    private static ImportMatcher.Existing existing(Transaction t, Set<Long> payPalAccounts) {
        ImportMatcher.PayPal payPal = payPalAccounts.contains(t.getAccount().getId()) ? ImportMatcher.PayPal.ACCOUNT
                : ImportRules.namesPayPal(t.getCounterparty(), t.getDescription(), null) ? ImportMatcher.PayPal.NAMED
                : ImportMatcher.PayPal.NONE;
        return new ImportMatcher.Existing(t.getId(), t.getAccount().getId(), t.getDate(), t.getAmount(),
                t.getImportBatch() != null, payPal, t.getDescription());
    }

    // R27 (c) and decision 95: a row of a PayPal file, or of a source on a PayPal account, is on PayPal
    private static ImportMatcher.Row matcherRow(Candidate c, BankProfile profile, Long accountId, Set<Long> payPalAccounts) {
        BankTransaction row = c.line().row();
        boolean onPayPal = profile == BankProfile.PAYPAL || (accountId != null && payPalAccounts.contains(accountId));
        ImportMatcher.PayPal payPal = onPayPal ? ImportMatcher.PayPal.ACCOUNT
                : ImportRules.namesPayPal(row.counterpartyName(), c.description(), row.counterpartyBic())
                        ? ImportMatcher.PayPal.NAMED : ImportMatcher.PayPal.NONE;
        return new ImportMatcher.Row(c.index(), c.source(), accountId, row.bookingDate(), row.amount(),
                row.counterpartyIban(), payPal, c.description(),
                profile == BankProfile.PAYPAL && ImportRules.isPayPalTransferKind(row.bankTransactionCode()));
    }

    // R31: monthly series among the new rows that are no transfer (except saving, decision 67) and fulfil no term
    private static List<ImportPreviewResponse.SeriesSuggestion> seriesSuggestions(List<Candidate> fresh,
            Map<Integer, ImportMatcher.Transfer> transfers, Set<Integer> savingRows, Map<Integer, Planned> matches,
            Map<Integer, Suggestion> suggestions) {
        Map<Integer, Candidate> byIndex = new HashMap<>();
        List<SeriesDetector.Row> rows = new ArrayList<>();
        for (Candidate c : fresh) {
            boolean booking = !transfers.containsKey(c.index()) || savingRows.contains(c.index());
            if (booking && !matches.containsKey(c.index())) {
                byIndex.put(c.index(), c);
                rows.add(new SeriesDetector.Row(c.index(), c.line().row().bookingDate(), c.line().row().amount(),
                        CategorySuggester.key(c.text()), c.description()));
            }
        }
        return SeriesDetector.detect(rows).stream().map(series -> {
            List<Candidate> covered = series.indices().stream().map(byIndex::get).toList();
            Map<Long, Long> votes = covered.stream()
                    .map(c -> suggestions.get(c.index()))
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.groupingBy(Suggestion::categoryId,
                            java.util.stream.Collectors.counting()));
            Long category = votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            boolean saving = covered.stream().allMatch(c -> savingRows.contains(c.index()));
            return new ImportPreviewResponse.SeriesSuggestion(
                    covered.stream().map(c -> c.line().number()).toList(), series.startDate(), series.amount(),
                    series.lowest(), series.highest(), series.description(), covered.getLast().source(),
                    saving ? null : category, saving);
        }).toList();
    }

    // P5: open terms around the dates of the file that the rows (no transfers, except saving) fulfil; since decision 67
    // also the terms of saving items, which have no category
    private Map<Integer, Planned> occurrenceMatches(
            List<Candidate> fresh, Map<Integer, ImportMatcher.Transfer> transfers, Set<Integer> savingRows,
            Map<Integer, Suggestion> suggestions, Map<Long, Long> autoEntries) {
        List<TermMatcher.Booking> bookings = fresh.stream()
                .filter(c -> !transfers.containsKey(c.index()) || savingRows.contains(c.index()))
                // Ticket #142: the counterparty and the description, like the bookings on the planning page
                .map(c -> new TermMatcher.Booking(c.index(), c.line().row().bookingDate(), c.line().row().amount(),
                        c.counterparty() != null ? c.counterparty() + " " + c.description() : c.description(),
                        suggestions.containsKey(c.index()) ? suggestions.get(c.index()).categoryId() : null,
                        savingRows.contains(c.index())))
                .toList();
        if (bookings.isEmpty()) {
            return Map.of();
        }
        LocalDate from = bookings.stream().map(TermMatcher.Booking::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = bookings.stream().map(TermMatcher.Booking::date).max(Comparator.naturalOrder()).orElseThrow();
        List<Planned> planned = new ArrayList<>();
        // Wide enough for the tolerance plus a weekend and for the early income of decision 78
        int window = TermSchedule.EARLY_INCOME_DAYS;
        for (TermService.ImportTerm term : termService.importTerms(from.minusDays(window), to.plusDays(window))) {
            planned.add(term.planned());
            if (term.autoEntryId() != null) {
                autoEntries.put(autoKey(term.planned()), term.autoEntryId());
            }
        }
        return TermMatcher.match(bookings, planned);
    }

    /**
     * Decision 67: the transfers (R27) that bring money from an everyday account, or a new one, to a savings or depot
     * account: a negative amount whose other side (the own account with the IBAN of the counterparty, the stored
     * counterpart or the counterpart row of the file) is on an account with that role.
     */
    private static Set<Integer> savingRows(List<Candidate> fresh, Map<Integer, ImportMatcher.Transfer> transfers,
            List<Candidate> candidates, Map<String, Source> sources, List<BankAccount> accounts,
            Map<Long, Transaction> storedById) {
        Map<Long, BankAccount> byId = new HashMap<>();
        accounts.forEach(account -> byId.put(account.getId(), account));
        Set<Integer> result = new HashSet<>();
        for (Candidate c : fresh) {
            ImportMatcher.Transfer transfer = transfers.get(c.index());
            if (transfer == null || c.line().row().amount().signum() >= 0) {
                continue;
            }
            BankAccount own = accountOf(c, sources, byId);
            if (own != null && own.getRole() != AccountRole.EVERYDAY) {
                continue;
            }
            String iban = Iban.normalize(c.line().row().counterpartyIban());
            BankAccount peer = iban == null ? null : accounts.stream()
                    .filter(account -> iban.equals(account.getIban()) && account != own)
                    .findFirst()
                    .orElse(null);
            if (peer == null && transfer.peerTransactionId() != null) {
                Transaction stored = storedById.get(transfer.peerTransactionId());
                peer = stored == null ? null : stored.getAccount();
            }
            if (peer == null && transfer.peerIndex() != null) {
                peer = accountOf(candidates.get(transfer.peerIndex()), sources, byId);
            }
            if (peer != null && peer.getRole() != AccountRole.EVERYDAY) {
                result.add(c.index());
            }
        }
        return result;
    }

    // The own account a source of the file goes to so far; null for a new account (everyday by default)
    private static BankAccount accountOf(Candidate c, Map<String, Source> sources, Map<Long, BankAccount> byId) {
        Long id = sources.get(c.source()).accountId();
        return id == null ? null : byId.get(id);
    }

    // A term by item version and planned date, for the automatic entries of the preview
    private static long autoKey(Planned planned) {
        return planned.itemId() * 100_000L + planned.date().toEpochDay();
    }

    // R27: the counterpart of a transfer for the hint of the preview; a row of the file with the name of its source
    private static ImportPreviewResponse.TransferPeer transferPeer(ImportMatcher.Transfer transfer,
            List<Candidate> candidates, Map<Long, Transaction> storedById, Map<String, Source> sources) {
        if (transfer == null) {
            return null;
        }
        if (transfer.peerIndex() != null) {
            Candidate peer = candidates.get(transfer.peerIndex());
            return new ImportPreviewResponse.TransferPeer(peer.line().number(), peer.line().row().bookingDate(),
                    peer.description(), peer.line().row().amount(), sources.get(peer.source()).name());
        }
        Transaction stored = transfer.peerTransactionId() == null ? null : storedById.get(transfer.peerTransactionId());
        return stored == null ? null
                : new ImportPreviewResponse.TransferPeer(null, stored.getDate(), stored.getDescription(),
                        stored.getAmount(), stored.getAccount().getName());
    }

    private static Entry entry(Candidate c, Status status, String reason, Suggestion suggestion,
            ImportMatcher.Transfer transfer, ImportPreviewResponse.TransferPeer transferPeer, Planned match,
            Long autoEntry, DuplicateOf duplicateOf, boolean saving) {
        BankTransaction row = c.line().row();
        return new Entry(c.line().number(), status, reason, c.fingerprint(), c.source(), row.bookingDate(), row.amount(),
                c.description(), c.counterparty(), c.bankCategory(), CategorySuggester.key(c.text()),
                suggestion == null ? null : suggestion.categoryId(), suggestion != null && suggestion.certain(),
                transfer != null, transfer != null && transfer.certain(),
                transfer == null ? null : transfer.peerTransactionId(), transferPeer,
                match == null ? null
                        : new Replaces(match.itemId(), match.date(), match.description(), match.amount().abs(),
                                match.categoryId(), autoEntry),
                duplicateOf, details(row), saving);
    }

    // Issue #119: the fields of the bank for the details of a row; the purpose with its whitespace joined, at most 500
    private static ImportPreviewResponse.Details details(BankTransaction row) {
        String purpose = row.remittanceInformation() == null ? null
                : row.remittanceInformation().replaceAll("\\s+", " ").trim();
        if (purpose != null && purpose.length() > MAX_PURPOSE) {
            purpose = purpose.substring(0, MAX_PURPOSE - 1) + "…";
        }
        return new ImportPreviewResponse.Details(blankToNull(row.counterpartyIban()), blankToNull(purpose),
                blankToNull(row.bankTransactionCode()), row.valueDate(), blankToNull(row.mandateId()),
                blankToNull(row.creditorId()), row.charges());
    }

    private static DuplicateOf duplicateOf(Transaction transaction) {
        return new DuplicateOf(transaction.getId(), transaction.getDate(), transaction.getDescription());
    }

    private static String cut(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 99) + "…";
    }

    // --- store ---

    /**
     * A checked row (index in the request): its category (null for a transfer), the stored counterpart that becomes a
     * transfer, the term of a planned item it fulfils (P5) or the new item it belongs to (R31).
     */
    private record Checked(int index, ImportRequest.Row row, Category category, Transaction peer,
            TermRef replaced, Integer series) {
    }

    /**
     * Stores the chosen rows (US33): everything is checked first, then the sources go to their accounts (a new name
     * that an account has already is that account), rows imported before are left out, and the counterpart of a
     * transfer becomes a transfer as well (R27).
     */
    @Transactional
    public ImportResultResponse commit(ImportRequest request) {
        User owner = currentUser.get();
        // Decision 92: a balance of the file alone may be taken over (stage 1 stores no rows, decision 91)
        if (request.rows().isEmpty() && request.sources().stream().noneMatch(ImportRequest.SourceMapping::takesBalance)) {
            throw new InvalidFieldException("rows", "Es ist keine Zeile ausgewählt.");
        }
        Map<String, BankAccount> existingAccounts = new HashMap<>();
        Map<String, String> newAccounts = new LinkedHashMap<>();
        List<BankAccount> own = bankAccountService.findAllOrdered();
        for (int i = 0; i < request.sources().size(); i++) {
            ImportRequest.SourceMapping mapping = request.sources().get(i);
            String field = "sources[" + i + "].accountId";
            if (mapping.accountId() != null) {
                try {
                    existingAccounts.put(mapping.key(), bankAccountService.findBookable(mapping.accountId()));
                } catch (InvalidFieldException e) {
                    throw new InvalidFieldException(field, e.getMessage());
                }
            } else if (mapping.newAccountName() != null && !mapping.newAccountName().isBlank()) {
                String name = mapping.newAccountName().trim();
                own.stream().filter(a -> a.getName().equalsIgnoreCase(name)).findFirst().ifPresentOrElse(
                        account -> existingAccounts.put(mapping.key(), account),
                        () -> newAccounts.put(mapping.key(), name));
            } else {
                throw new InvalidFieldException(field, "Wähle ein Konto für diese Quelle.");
            }
        }

        // R31: the series the import creates
        List<ImportRequest.NewSeries> newSeries = request.seriesOrEmpty();
        for (int i = 0; i < newSeries.size(); i++) {
            ImportRequest.NewSeries series = newSeries.get(i);
            String field = "series[" + i + "]";
            if (!existingAccounts.containsKey(series.source()) && !newAccounts.containsKey(series.source())) {
                throw new InvalidFieldException(field + ".source", "Für diese Quelle fehlt die Zuordnung zu einem Konto.");
            }
            if (series.amount().signum() == 0) {
                throw new InvalidFieldException(field + ".amount", "Der Betrag darf nicht 0 sein.");
            }
            if (series.isSaving()) {
                if (series.amount().signum() > 0) {
                    throw new InvalidFieldException(field + ".amount", "Sparen ist ein negativer Betrag.");
                }
                if (series.categoryId() != null) {
                    throw new InvalidFieldException(field + ".categoryId", "Ein Sparposten hat keine Kategorie.");
                }
                continue;
            }
            if (series.categoryId() == null) {
                throw new InvalidFieldException(field + ".categoryId", "Die Kategorie ist Pflicht.");
            }
            try {
                categoryService.findBookable(series.categoryId());
            } catch (InvalidFieldException e) {
                throw new InvalidFieldException(field + ".categoryId", e.getMessage());
            }
        }

        Set<String> imported = new HashSet<>(transactionRepository.findImportedFingerprints(owner,
                request.rows().stream().map(ImportRequest.Row::fingerprint).toList()));
        List<Checked> checked = new ArrayList<>();
        Set<TermRef> replacedOccurrences = new HashSet<>();
        int skipped = 0;
        for (int i = 0; i < request.rows().size(); i++) {
            ImportRequest.Row row = request.rows().get(i);
            String field = "rows[" + i + "]";
            if (!existingAccounts.containsKey(row.source()) && !newAccounts.containsKey(row.source())) {
                throw new InvalidFieldException(field + ".source", "Für diese Quelle fehlt die Zuordnung zu einem Konto.");
            }
            if (row.amount().signum() == 0) {
                throw new InvalidFieldException(field + ".amount", "Der Betrag darf nicht 0 sein.");
            }
            Category category = categoryOf(row, field);
            Transaction peer = row.transferPeerId() == null ? null : peerOf(owner, row, field);
            TermRef replaced = null;
            if (row.replaces() != null) {
                if (row.isPlainTransfer()) {
                    throw new InvalidFieldException(field + ".replaces", "Eine Umbuchung erfüllt keinen Termin.");
                }
                replaced = row.replaces();
                if (!replacedOccurrences.add(row.replaces())) {
                    throw new InvalidFieldException(field + ".replaces", "Dieses Vorkommen ersetzt schon eine andere Zeile.");
                }
            }
            if (row.series() != null) {
                if (row.series() < 0 || row.series() >= newSeries.size()) {
                    throw new InvalidFieldException(field + ".series", "Diese Serie gibt es nicht.");
                }
                if (row.isPlainTransfer()) {
                    throw new InvalidFieldException(field + ".series", "Eine Umbuchung kann zu keiner Serie gehören.");
                }
                // Decision 67: the rows of a saving series are saving, the rows of another series are not
                if (newSeries.get(row.series()).isSaving() != row.isSaving()) {
                    throw new InvalidFieldException(field + ".series", newSeries.get(row.series()).isSaving()
                            ? "Die Zeilen eines Sparpostens sind Sparen." : "Eine Sparzeile gehört zu keinem Posten mit Kategorie.");
                }
                if (row.replaces() != null) {
                    throw new InvalidFieldException(field + ".series",
                            "Die Zeile ersetzt schon ein geplantes Vorkommen einer anderen Serie.");
                }
            }
            if (!imported.add(row.fingerprint())) {
                skipped++;
                continue;
            }
            checked.add(new Checked(i, row, category, peer, replaced, row.series()));
        }
        Map<Integer, LocalDate> seriesOccurrences = seriesOccurrences(newSeries, checked);

        Map<String, BankAccount> accounts = new HashMap<>(existingAccounts);
        Map<String, BankAccount> createdByName = new HashMap<>();
        newAccounts.forEach((key, name) -> accounts.put(key,
                createdByName.computeIfAbsent(name.toLowerCase(java.util.Locale.GERMAN),
                        lower -> bankAccountService.create(name, null))));
        accounts.forEach((key, account) -> sourceRepository.findByOwnerAndSourceKey(owner, key).ifPresentOrElse(
                source -> source.moveTo(account),
                () -> sourceRepository.save(new BankAccountSource(owner, account, key))));
        // Decision 92: the balance of the file becomes the balance of the account, unless it has a newer one
        for (ImportRequest.SourceMapping mapping : request.sources()) {
            BankAccount account = accounts.get(mapping.key());
            if (mapping.takesBalance() && account != null) {
                takeBalance(owner, account, mapping.balanceDate(), mapping.balanceAmount());
            }
        }
        if (checked.isEmpty()) {
            return new ImportResultResponse(null, 0, skipped, 0, List.of());
        }

        LocalDate first = checked.stream().map(c -> c.row().date()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate last = checked.stream().map(c -> c.row().date()).max(Comparator.naturalOrder()).orElseThrow();
        ImportBatch batch = batchRepository.save(
                new ImportBatch(owner, request.profile(), Instant.now(clock), first, last, checked.size()));
        List<Transaction> created = new ArrayList<>();
        for (Checked c : checked) {
            ImportRequest.Row row = c.row();
            Transaction transaction = new Transaction(owner, row.amount().setScale(2, RoundingMode.HALF_UP),
                    row.description().trim(), row.date(), c.category(), accounts.get(row.source()),
                    Boolean.TRUE.equals(row.transfer()) || row.isSaving());
            transaction.markImported(batch, row.fingerprint(), blankToNull(row.counterparty()),
                    blankToNull(row.bankCategory()));
            // P1, decision 67: the kind Sparen
            transaction.markSaving(row.isSaving());
            created.add(transaction);
            if (c.peer() != null && !c.peer().isTransfer()) {
                c.peer().makeTransfer(batch);
            }
        }
        transactionRepository.saveAll(created);
        Map<Integer, PlannedItem> createdSeries = new HashMap<>();
        int replacedAuto = 0;
        for (int i = 0; i < checked.size(); i++) {
            Checked c = checked.get(i);
            if (c.replaced() != null && termService.link(c.replaced().itemId(), c.replaced().date(), created.get(i),
                    "rows[" + c.index() + "].replaces")) {
                replacedAuto++;
            }
            if (c.series() != null) {
                PlannedItem item = createdSeries.computeIfAbsent(c.series(), index -> {
                    ImportRequest.NewSeries series = newSeries.get(index);
                    return series.isSaving()
                            ? planService.createMonthlySavingForImport(series.description(), series.amount(),
                                    accounts.get(series.source()), series.startDate(), batch)
                            : planService.createMonthlyForImport(series.description(), series.amount(),
                                    series.categoryId(), accounts.get(series.source()), series.startDate(), batch);
                });
                termService.link(item.getItemKey(), seriesOccurrences.get(c.index()), created.get(i));
            }
        }
        // P6: automatic entries of the accounts of the file within its dates that no row replaced
        transactionRepository.flush();
        java.util.Set<Long> accountIds = new java.util.HashSet<>();
        accounts.values().forEach(account -> accountIds.add(account.getId()));
        List<ImportResultResponse.MissingAuto> missing = transactionRepository
                .findByOwnerAndDateBetweenOrderByDateDescIdDesc(owner, first, last).stream()
                .filter(t -> t.isAuto() && accountIds.contains(t.getAccount().getId()))
                .map(t -> new ImportResultResponse.MissingAuto(t.getId(), t.getDate(), t.getDescription(),
                        t.getAmount(), t.getAccount().getName()))
                .toList();
        return new ImportResultResponse(ImportBatchResponse.from(batch), created.size(), skipped, replacedAuto, missing);
    }

    // R31: per row index the occurrence of its new series it replaces; 400 for a row without one at most 5 days away.
    // A series without a row in the import is not created.
    private static Map<Integer, LocalDate> seriesOccurrences(List<ImportRequest.NewSeries> newSeries, List<Checked> checked) {
        Map<Integer, LocalDate> result = new HashMap<>();
        for (int s = 0; s < newSeries.size(); s++) {
            int index = s;
            List<Checked> rows = checked.stream().filter(c -> Integer.valueOf(index).equals(c.series())).toList();
            List<LocalDate> dates = SeriesDetector.occurrenceDates(newSeries.get(s).startDate(),
                    rows.stream().map(c -> c.row().date()).toList());
            for (int i = 0; i < rows.size(); i++) {
                result.put(rows.get(i).index(), dates.get(i));
            }
        }
        checked.stream().filter(c -> c.series() != null && result.get(c.index()) == null).findFirst().ifPresent(c -> {
            throw new InvalidFieldException("rows[" + c.index() + "].series",
                    "Kein Vorkommen der Serie liegt höchstens " + SeriesDetector.MAX_DAYS + " Tage von dieser Zeile entfernt.");
        });
        return result;
    }

    // R27: a transfer has no category; R40, R37: every other row may be without one (an expense without category)
    private Category categoryOf(ImportRequest.Row row, String field) {
        if (Boolean.TRUE.equals(row.transfer()) || row.isSaving()) {
            if (row.categoryId() != null) {
                throw new InvalidFieldException(field + ".categoryId", "Eine Umbuchung hat keine Kategorie.");
            }
            return null;
        }
        if (row.transferPeerId() != null) {
            throw new InvalidFieldException(field + ".transferPeerId", "Nur eine Umbuchung hat eine Gegenbuchung.");
        }
        if (row.categoryId() == null) {
            return null;
        }
        try {
            return categoryService.findBookable(row.categoryId());
        } catch (InvalidFieldException e) {
            throw new InvalidFieldException(field + ".categoryId", e.getMessage());
        }
    }

    // R27: the stored counterpart must be the user's own booking (400 otherwise, R15)
    private Transaction peerOf(User owner, ImportRequest.Row row, String field) {
        return transactionRepository.findByIdAndOwner(row.transferPeerId(), owner)
                .orElseThrow(() -> new InvalidFieldException(field + ".transferPeerId", "Die Gegenbuchung existiert nicht."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // --- list and undo ---

    @Transactional(readOnly = true)
    public List<ImportBatchResponse> findAll() {
        return batchRepository.findByOwnerOrderByCreatedAtDescIdDesc(currentUser.get()).stream()
                .map(ImportBatchResponse::from)
                .toList();
    }

    /** US34: deletes every transaction of the import; another user's import is 404 (R15). */
    // The bulk deletes clear the persistence context, so the batch is deleted by id
    @Transactional
    public void delete(long id) {
        ImportBatch batch = batchRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Import " + id + " wurde nicht gefunden."));
        // R27: stored counterparts the import made transfers get their category back
        transactionRepository.findByTransferBatch(batch).forEach(Transaction::undoTransfer);
        // P5: terms the import fulfilled are open again
        termRepository.deleteLinkedToBatch(batch);
        // R31: the planned items the import created go
        List<java.util.UUID> createdKeys = itemRepository.findKeysByImportBatch(batch);
        if (!createdKeys.isEmpty()) {
            termRepository.deleteByItemKeys(createdKeys);
        }
        itemRepository.deleteByImportBatch(batch);
        transactionRepository.deleteByImportBatch(batch);
        batchRepository.deleteById(id);
    }
}
