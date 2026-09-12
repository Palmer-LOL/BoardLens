package lol.palmer.boardlens.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mutable orthodox-chess placement and FEN metadata, with no platform dependencies.
 * Indices run from a8 (0) through h8 (7) to a1 (56) and h1 (63); '.' means empty.
 *
 * <p>Parsing enforces FEN syntax. {@link #validationIssues()} separately reports
 * structural and historical inconsistencies, allowing incomplete boards to be
 * edited. An empty issue list is not proof of chess legality: this class does not
 * generate moves, examine checks or establish whether a position is reachable.
 *
 * <p>Metadata is deliberately public for editing. It is checked by serialization
 * and diagnostics, never inferred from piece locations. Instances are not thread-safe.
 */
public final class Position {
    private static final String PIECE_CHARS = "PNBRQKpnbrqk";
    private static final String CASTLING_ORDER = "KQkq";
    private static final String STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final char[] board;

    public char activeColor = 'w';
    public String castling = "-";
    public String enPassant = "-";
    public int halfmove = 0;
    public int fullmove = 1;

    /** Takes ownership of an internally allocated, validated array. */
    private Position(char[] board) {
        this.board = board;
    }

    /** Returns an empty editable board with metadata {@code w - - 0 1}. */
    public static Position empty() {
        char[] board = new char[64];
        Arrays.fill(board, '.');
        return new Position(board);
    }

    /** Returns the orthodox starting position, including all four castling rights. */
    public static Position starting() {
        return parse(STARTING_FEN);
    }

    /**
     * Parses exactly six ASCII-whitespace-separated FEN fields. Surrounding ASCII
     * whitespace is accepted. Castling rights must be unique and in KQkq order;
     * en-passant is '-' or a rank-6 target for White / rank-3 target for Black.
     * Counters contain only ASCII digits and fit in an int; leading zeros are
     * accepted and are removed on serialization.
     *
     * @throws IllegalArgumentException for null input or malformed FEN syntax
     */
    public static Position parse(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN must not be null.");
        }
        // split discards trailing separators but preserves an initial empty field.
        String[] fields = fen.split("[ \\t\\n\\x0B\\f\\r]+");
        int first = fields.length > 0 && fields[0].isEmpty() ? 1 : 0;
        if (fields.length - first != 6) {
            throw new IllegalArgumentException("FEN must contain exactly six fields.");
        }
        Position position = fromPlacement(fields[first]);
        if (fields[first + 1].length() != 1) {
            throw new IllegalArgumentException("Active color must be 'w' or 'b'.");
        }
        position.activeColor = fields[first + 1].charAt(0);
        position.castling = fields[first + 2];
        position.enPassant = fields[first + 3];
        position.halfmove = parseCounter(fields[first + 4], "Halfmove", 0);
        position.fullmove = parseCounter(fields[first + 5], "Fullmove", 1);
        position.requireValidMetadata();
        return position;
    }

    /**
     * Parses only the placement field, without whitespace or metadata, and uses
     * metadata {@code w - - 0 1}. Each of eight ranks must describe eight squares
     * using PNBRQKpnbrqk and digits 1 through 8. Adjacent empty-run digits are
     * rejected; '.' is an editing representation, not a FEN character.
     *
     * @throws IllegalArgumentException for null input or malformed placement
     */
    public static Position fromPlacement(String placement) {
        if (placement == null) {
            throw new IllegalArgumentException("Placement must not be null.");
        }
        String[] ranks = placement.split("/", -1);
        if (ranks.length != 8) {
            throw new IllegalArgumentException("Placement must contain exactly eight ranks.");
        }
        Position position = empty();
        for (int row = 0; row < 8; row++) {
            int file = 0;
            boolean previousWasDigit = false;
            String rank = ranks[row];
            for (int i = 0; i < rank.length(); i++) {
                char symbol = rank.charAt(i);
                if (symbol >= '1' && symbol <= '8') {
                    if (previousWasDigit) {
                        throw new IllegalArgumentException(
                                "Adjacent empty-run digits in rank " + (8 - row) + ".");
                    }
                    file += symbol - '0';
                    previousWasDigit = true;
                } else if (PIECE_CHARS.indexOf(symbol) >= 0) {
                    if (file >= 8) {
                        throw rankWidthProblem(row);
                    }
                    position.board[row * 8 + file] = symbol;
                    file++;
                    previousWasDigit = false;
                } else {
                    throw new IllegalArgumentException(
                            "Invalid placement character in rank " + (8 - row) + ": " + symbol);
                }
                if (file > 8) {
                    throw rankWidthProblem(row);
                }
            }
            if (file != 8) {
                throw rankWidthProblem(row);
            }
        }
        return position;
    }

    /** Copies placement and metadata verbatim, including metadata being edited. */
    public Position copy() {
        Position result = new Position(board.clone());
        result.activeColor = activeColor;
        result.castling = castling;
        result.enPassant = enPassant;
        result.halfmove = halfmove;
        result.fullmove = fullmove;
        return result;
    }

    /** Returns a defensive snapshot of the 64 squares. */
    public char[] pieces() {
        return board.clone();
    }

    /** @throws IndexOutOfBoundsException unless index is in [0, 63] */
    public char pieceAt(int index) {
        checkIndex(index);
        return board[index];
    }

    /**
     * Edits one square without changing any history metadata.
     *
     * @throws IndexOutOfBoundsException unless index is in [0, 63]
     * @throws IllegalArgumentException unless piece is '.' or one of PNBRQKpnbrqk
     */
    public void setPiece(int index, char piece) {
        checkIndex(index);
        if (piece != '.' && PIECE_CHARS.indexOf(piece) < 0) {
            throw new IllegalArgumentException("Piece must be '.' or one of " + PIECE_CHARS + ".");
        }
        board[index] = piece;
    }

    /** Serializes placement with maximal empty-square runs, regardless of metadata. */
    public String placement() {
        StringBuilder result = new StringBuilder(71);
        for (int row = 0; row < 8; row++) {
            if (row != 0) {
                result.append('/');
            }
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                char piece = board[row * 8 + file];
                if (piece == '.') {
                    emptyCount++;
                } else {
                    if (emptyCount != 0) {
                        result.append(emptyCount);
                        emptyCount = 0;
                    }
                    result.append(piece);
                }
            }
            if (emptyCount != 0) {
                result.append(emptyCount);
            }
        }
        return result.toString();
    }

    /**
     * Serializes six fields with single spaces. Structural/history warnings do
     * not prevent serialization or cause metadata to be repaired.
     *
     * @throws IllegalArgumentException if public metadata has malformed FEN syntax
     */
    public String toFen() {
        requireValidMetadata();
        return placement() + " " + activeColor + " " + castling + " " + enPassant
                + " " + halfmove + " " + fullmove;
    }

    /**
     * Returns a fresh list of human-readable structural and history diagnostics,
     * without mutating the position. Includes malformed public metadata, king
     * counts/adjacency, pawn ranks, material limits, castling home squares, and
     * en-passant pawn/target/origin/clock consistency. No capturing pawn is needed
     * for a FEN en-passant target. Promotion counts and chess legality are not proven.
     */
    public List<String> validationIssues() {
        List<String> issues = metadataIssues();
        int[] kings = new int[2];
        int[] pawns = new int[2];
        int[] pieces = new int[2];
        for (int index = 0; index < 64; index++) {
            char piece = board[index];
            if (piece == '.') {
                continue;
            }
            int color = piece >= 'A' && piece <= 'Z' ? 0 : 1;
            pieces[color]++;
            if (piece == 'K' || piece == 'k') {
                kings[color]++;
            }
            if (piece == 'P' || piece == 'p') {
                pawns[color]++;
                if (index < 8 || index >= 56) {
                    issues.add(colorName(color) + " pawn on back rank at " + squareName(index) + ".");
                }
            }
        }
        for (int color = 0; color < 2; color++) {
            String name = colorName(color);
            if (kings[color] != 1) {
                issues.add(name + " must have exactly one king (found " + kings[color] + ").");
            }
            if (pawns[color] > 8) {
                issues.add(name + " has " + pawns[color] + " pawns; at most 8 are possible.");
            }
            if (pieces[color] > 16) {
                issues.add(name + " has " + pieces[color] + " pieces; at most 16 are possible.");
            }
        }
        // Scan every king pair, including positions with surplus kings under editing.
        for (int white = 0; white < 64; white++) {
            if (board[white] != 'K') {
                continue;
            }
            for (int black = 0; black < 64; black++) {
                if (board[black] == 'k' && Math.abs(white / 8 - black / 8) <= 1
                        && Math.abs(white % 8 - black % 8) <= 1) {
                    issues.add("Kings are adjacent at " + squareName(white)
                            + " and " + squareName(black) + ".");
                }
            }
        }
        if (castlingProblem(castling) == null && !"-".equals(castling)) {
            for (int i = 0; i < castling.length(); i++) {
                char right = castling.charAt(i);
                boolean white = right == 'K' || right == 'Q';
                int kingIndex = white ? 60 : 4;
                int rookIndex = (white ? 56 : 0) + (right == 'K' || right == 'k' ? 7 : 0);
                if (board[kingIndex] != (white ? 'K' : 'k')) {
                    issues.add("Castling " + right + " requires a " + (white ? "white" : "black")
                            + " king on " + squareName(kingIndex) + ".");
                }
                if (board[rookIndex] != (white ? 'R' : 'r')) {
                    issues.add("Castling " + right + " requires a " + (white ? "white" : "black")
                            + " rook on " + squareName(rookIndex) + ".");
                }
            }
        }
        if (validActiveColor() && enPassantProblem() == null && !"-".equals(enPassant)) {
            int target = (8 - (enPassant.charAt(1) - '0')) * 8 + enPassant.charAt(0) - 'a';
            boolean blackMoved = activeColor == 'w';
            int pawnIndex = target + (blackMoved ? 8 : -8);
            int originIndex = target + (blackMoved ? -8 : 8);
            if (board[target] != '.') {
                issues.add("En-passant target " + enPassant + " must be empty.");
            }
            if (board[pawnIndex] != (blackMoved ? 'p' : 'P')) {
                issues.add("En-passant target " + enPassant + " requires a "
                        + (blackMoved ? "black" : "white") + " pawn on " + squareName(pawnIndex) + ".");
            }
            if (board[originIndex] != '.') {
                issues.add("En-passant pawn origin " + squareName(originIndex) + " must be empty.");
            }
            if (halfmove != 0) {
                issues.add("En-passant requires a halfmove clock of 0 after the pawn move.");
            }
            if (blackMoved && fullmove == 1) {
                issues.add("En-passant after a black move requires a fullmove number of at least 2.");
            }
        }
        if (validActiveColor() && fullmove >= 1 && halfmove >= 0) {
            // Cast before arithmetic so maximum accepted int counters cannot overflow.
            long elapsedPlies = 2L * (fullmove - 1L) + (activeColor == 'b' ? 1 : 0);
            if (halfmove > elapsedPlies) {
                issues.add("Halfmove clock exceeds the plies allowed by active color and fullmove number.");
            }
        }
        return issues;
    }

    /** @throws IndexOutOfBoundsException unless index is in [0, 63] */
    public static String squareName(int index) {
        checkIndex(index);
        return new String(new char[] {(char) ('a' + index % 8), (char) ('8' - index / 8)});
    }

    /**
     * Physically rotates placement 90 degrees clockwise (a8 to h8). Piece colors,
     * active color and counters are preserved; castling and en-passant become '-'.
     */
    public void rotateClockwise() {
        char[] rotated = new char[64];
        for (int row = 0; row < 8; row++) {
            for (int file = 0; file < 8; file++) {
                rotated[file * 8 + 7 - row] = board[row * 8 + file];
            }
        }
        System.arraycopy(rotated, 0, board, 0, board.length);
        castling = "-";
        enPassant = "-";
    }

    /** Physically rotates placement 180 degrees (a8 to h1), with the same metadata policy. */
    public void rotate180() {
        for (int index = 0; index < 32; index++) {
            char piece = board[index];
            board[index] = board[63 - index];
            board[63 - index] = piece;
        }
        castling = "-";
        enPassant = "-";
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= 64) {
            throw new IndexOutOfBoundsException("Square index must be in [0, 63]: " + index);
        }
    }

    private static IllegalArgumentException rankWidthProblem(int row) {
        return new IllegalArgumentException("Rank " + (8 - row) + " must describe exactly eight squares.");
    }

    private static int parseCounter(String value, String name, int minimum) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " counter must contain ASCII digits.");
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                throw new IllegalArgumentException(name + " counter must contain only ASCII digits.");
            }
        }
        final int result;
        try {
            result = Integer.parseInt(value);
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException(name + " counter exceeds " + Integer.MAX_VALUE + ".", overflow);
        }
        if (result < minimum) {
            throw new IllegalArgumentException(name + " counter must be at least " + minimum + ".");
        }
        return result;
    }

    private static String colorName(int color) {
        return color == 0 ? "White" : "Black";
    }

    private boolean validActiveColor() {
        return activeColor == 'w' || activeColor == 'b';
    }

    private static String castlingProblem(String rights) {
        if ("-".equals(rights)) {
            return null;
        }
        String problem = "Castling must be '-' or unique rights in KQkq order.";
        if (rights == null || rights.isEmpty()) {
            return problem;
        }
        int previous = -1;
        for (int i = 0; i < rights.length(); i++) {
            int order = CASTLING_ORDER.indexOf(rights.charAt(i));
            if (order <= previous) {
                return problem;
            }
            previous = order;
        }
        return null;
    }

    private String enPassantProblem() {
        if ("-".equals(enPassant)) {
            return null;
        }
        if (enPassant == null || enPassant.length() != 2
                || enPassant.charAt(0) < 'a' || enPassant.charAt(0) > 'h'
                || (enPassant.charAt(1) != '3' && enPassant.charAt(1) != '6')) {
            return "En-passant must be '-' or a square on rank 3 or 6 with a file from a to h.";
        }
        if (validActiveColor() && enPassant.charAt(1) != (activeColor == 'w' ? '6' : '3')) {
            return "En-passant rank must be 6 with White active or 3 with Black active.";
        }
        return null;
    }

    private List<String> metadataIssues() {
        List<String> issues = new ArrayList<>();
        if (!validActiveColor()) {
            issues.add("Active color must be 'w' or 'b'.");
        }
        String castlingIssue = castlingProblem(castling);
        if (castlingIssue != null) {
            issues.add(castlingIssue);
        }
        String enPassantIssue = enPassantProblem();
        if (enPassantIssue != null) {
            issues.add(enPassantIssue);
        }
        if (halfmove < 0) {
            issues.add("Halfmove clock must be nonnegative.");
        }
        if (fullmove < 1) {
            issues.add("Fullmove number must be positive.");
        }
        return issues;
    }

    private void requireValidMetadata() {
        List<String> issues = metadataIssues();
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException(issues.get(0));
        }
    }
}
