package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.bankaccount.BankAccountSource;
import io.github.mirco104.budget.bankaccount.BankAccountSourceRepository;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.importer.BankCsv.BankFile;
import io.github.mirco104.budget.importer.BankCsv.BankRow;
import io.github.mirco104.budget.importer.BankCsv.Line;
import io.github.mirco104.budget.importer.ImportPreviewResponse.DuplicateOf;
import io.github.mirco104.budget.importer.ImportPreviewResponse.Entry;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CSV import of the signed-in user (R15, R26 to R30, US33, US34). */
@Service
public class ImportService {

    /** R30: the largest file and the most lines. */
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_LINES = 5000;

    private final TransactionRepository transactionRepository;
    private final ImportBatchRepository batchRepository;
    private final BankAccountService bankAccountService;
    private final BankAccountSourceRepository sourceRepository;
    private final CategoryService categoryService;
    private final BookingSuggestionService suggestionService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ImportService(
            TransactionRepository transactionRepository, ImportBatchRepository batchRepository,
            BankAccountService bankAccountService, BankAccountSourceRepository sourceRepository,
            CategoryService categoryService, BookingSuggestionService suggestionService, CurrentUser currentUser,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.bankAccountService = bankAccountService;
        this.sourceRepository = sourceRepository;
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

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
        Map<String, Source> sources = new LinkedHashMap<>();
        for (Line line : file.lines()) {
            if (line.row() != null) {
                String key = ImportRules.sourceKey(profile, line.row().source());
                sources.computeIfAbsent(key, k -> source(profile, line.row().source(), k, remembered, accounts));
            }
        }

        // Fingerprints, with the number of equal rows (R30)
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Integer> equalRows = new HashMap<>();
        for (Line line : file.lines()) {
            BankRow row = line.row();
            if (row == null) {
                continue;
            }
            int number = equalRows.merge(ImportRules.sameRowKey(row), 1, Integer::sum);
            String counterparty = cut(row.counterparty());
            String description = ImportRules.describe(row.counterparty(), ImportRules.cleanPurpose(row.purpose()), row.kind());
            candidates.add(new Candidate(candidates.size(), line, ImportRules.sourceKey(profile, row.source()),
                    ImportRules.fingerprint(profile, row, number), description, counterparty, cut(row.bankCategory()),
                    counterparty != null ? counterparty : description));
        }
        Set<String> imported = candidates.isEmpty() ? Set.of()
                : new HashSet<>(transactionRepository.findImportedFingerprints(owner,
                        candidates.stream().map(Candidate::fingerprint).toList()));
        List<Candidate> fresh = candidates.stream().filter(c -> !imported.contains(c.fingerprint())).toList();

        // R27 and R30 against the stored single transactions around the dates of the file
        List<Transaction> stored = storedAround(owner, fresh);
        Map<Long, Transaction> storedById = stored.stream().collect(Collectors.toMap(Transaction::getId, Function.identity()));
        List<ImportMatcher.Existing> existing = stored.stream()
                .map(t -> new ImportMatcher.Existing(t.getId(), t.getAccount().getId(), t.getDate(), t.getAmount(),
                        t.getImportBatch() != null))
                .toList();
        List<ImportMatcher.Row> matcherRows = fresh.stream()
                .map(c -> new ImportMatcher.Row(c.index(), c.source(), sources.get(c.source()).accountId(),
                        c.line().row().date(), c.line().row().amount(), c.line().row().counterpartyIban()))
                .toList();
        List<ImportMatcher.OwnAccount> ownAccounts = accounts.stream()
                .map(a -> new ImportMatcher.OwnAccount(a.getId(), a.getIban()))
                .toList();
        Map<Integer, ImportMatcher.Transfer> transfers = ImportMatcher.transfers(matcherRows, existing, ownAccounts);
        Map<Integer, Long> duplicates = ImportMatcher.possibleDuplicates(matcherRows, existing);
        BookingSuggestionService.Suggester suggester = suggestionService.suggester();

        Map<Integer, Entry> entries = new HashMap<>();
        for (Candidate c : candidates) {
            BankRow row = c.line().row();
            if (imported.contains(c.fingerprint())) {
                entries.put(c.line().number(), entry(c, Status.DUPLICATE, "schon importiert", null, null, null));
                continue;
            }
            ImportMatcher.Transfer transfer = transfers.get(c.index());
            Long duplicateId = duplicates.get(c.index());
            Status status = duplicateId != null ? Status.POSSIBLE_DUPLICATE : Status.NEW;
            Suggestion suggestion = transfer != null ? null
                    : suggester.suggest(c.text(), row.amount().signum() > 0, c.bankCategory()).orElse(null);
            DuplicateOf duplicateOf = duplicateId == null ? null : duplicateOf(storedById.get(duplicateId));
            entries.put(c.line().number(), entry(c, status, null, suggestion, transfer, duplicateOf));
        }
        List<Entry> ordered = file.lines().stream()
                .map(line -> line.row() == null ? Entry.skipped(line.number(), line.skipReason()) : entries.get(line.number()))
                .toList();

        int duplicateCount = (int) ordered.stream().filter(e -> e.status() == Status.DUPLICATE).count();
        int skipped = (int) ordered.stream().filter(e -> e.status() == Status.SKIPPED).count();
        return new ImportPreviewResponse(profile, profile.label(), ordered.size(), ordered.size() - duplicateCount - skipped,
                duplicateCount, skipped, List.copyOf(sources.values()), ordered);
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
        return new Source(key, name == null || name.isBlank() ? profile.label() : name, accountId, suggestedName);
    }

    private List<Transaction> storedAround(User owner, List<Candidate> fresh) {
        if (fresh.isEmpty()) {
            return List.of();
        }
        LocalDate from = fresh.stream().map(c -> c.line().row().date()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = fresh.stream().map(c -> c.line().row().date()).max(Comparator.naturalOrder()).orElseThrow();
        return transactionRepository.findCountedBetween(owner, from.minusDays(ImportMatcher.TRANSFER_DAYS),
                to.plusDays(ImportMatcher.TRANSFER_DAYS));
    }

    private static Entry entry(Candidate c, Status status, String reason, Suggestion suggestion,
            ImportMatcher.Transfer transfer, DuplicateOf duplicateOf) {
        BankRow row = c.line().row();
        return new Entry(c.line().number(), status, reason, c.fingerprint(), c.source(), row.date(), row.amount(),
                c.description(), c.counterparty(), c.bankCategory(), CategorySuggester.key(c.text()),
                suggestion == null ? null : suggestion.categoryId(), suggestion != null && suggestion.certain(),
                transfer != null, transfer != null && transfer.certain(),
                transfer == null ? null : transfer.peerTransactionId(), duplicateOf);
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

    /** A checked row: its category (null for a transfer) and the stored counterpart that becomes a transfer. */
    private record Checked(ImportRequest.Row row, Category category, Transaction peer) {
    }

    /**
     * Stores the chosen rows (US33): everything is checked first, then the sources go to their accounts (a new name
     * that an account has already is that account), rows imported before are left out, and the counterpart of a
     * transfer becomes a transfer as well (R27).
     */
    @Transactional
    public ImportResultResponse commit(ImportRequest request) {
        User owner = currentUser.get();
        if (request.rows().isEmpty()) {
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

        Set<String> imported = new HashSet<>(transactionRepository.findImportedFingerprints(owner,
                request.rows().stream().map(ImportRequest.Row::fingerprint).toList()));
        List<Checked> checked = new ArrayList<>();
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
            if (!imported.add(row.fingerprint())) {
                skipped++;
                continue;
            }
            checked.add(new Checked(row, category, peer));
        }

        Map<String, BankAccount> accounts = new HashMap<>(existingAccounts);
        Map<String, BankAccount> createdByName = new HashMap<>();
        newAccounts.forEach((key, name) -> accounts.put(key,
                createdByName.computeIfAbsent(name.toLowerCase(java.util.Locale.GERMAN),
                        lower -> bankAccountService.create(name, null))));
        accounts.forEach((key, account) -> sourceRepository.findByOwnerAndSourceKey(owner, key).ifPresentOrElse(
                source -> source.moveTo(account),
                () -> sourceRepository.save(new BankAccountSource(owner, account, key))));
        if (checked.isEmpty()) {
            return new ImportResultResponse(null, 0, skipped);
        }

        LocalDate first = checked.stream().map(c -> c.row().date()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate last = checked.stream().map(c -> c.row().date()).max(Comparator.naturalOrder()).orElseThrow();
        ImportBatch batch = batchRepository.save(
                new ImportBatch(owner, request.profile(), Instant.now(clock), first, last, checked.size()));
        List<Transaction> created = new ArrayList<>();
        for (Checked c : checked) {
            ImportRequest.Row row = c.row();
            Transaction transaction = new Transaction(owner, row.amount().setScale(2, RoundingMode.HALF_UP),
                    row.description().trim(), row.date(), c.category(), accounts.get(row.source()));
            transaction.markImported(batch, row.fingerprint(), blankToNull(row.counterparty()),
                    blankToNull(row.bankCategory()));
            created.add(transaction);
            if (c.peer() != null && !c.peer().isTransfer()) {
                c.peer().makeTransfer();
            }
        }
        transactionRepository.saveAll(created);
        return new ImportResultResponse(ImportBatchResponse.from(batch), created.size(), skipped);
    }

    // R27: a transfer has no category; every other row needs one
    private Category categoryOf(ImportRequest.Row row, String field) {
        if (Boolean.TRUE.equals(row.transfer())) {
            if (row.categoryId() != null) {
                throw new InvalidFieldException(field + ".categoryId", "Eine Umbuchung hat keine Kategorie.");
            }
            return null;
        }
        if (row.transferPeerId() != null) {
            throw new InvalidFieldException(field + ".transferPeerId", "Nur eine Umbuchung hat eine Gegenbuchung.");
        }
        if (row.categoryId() == null) {
            throw new InvalidFieldException(field + ".categoryId", "Die Kategorie ist Pflicht.");
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
    @Transactional
    public void delete(long id) {
        ImportBatch batch = batchRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Import " + id + " wurde nicht gefunden."));
        transactionRepository.deleteByImportBatch(batch);
        batchRepository.deleteById(id);
    }
}
