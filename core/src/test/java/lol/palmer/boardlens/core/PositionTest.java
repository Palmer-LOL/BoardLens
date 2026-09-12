package lol.palmer.boardlens.core;

import java.util.List;
import java.util.Locale;

/** Dependency-free regression suite; run main with or without JVM assertions enabled. */
public final class PositionTest {
    private static final String EMPTY = "8/8/8/8/8/8/8/8";
    private static final String START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    private static final String KINGS = "4k3/8/8/8/8/8/8/4K3";
    private static final String HOME = "r3k2r/8/8/8/8/8/8/R3K2R";
    private static final String ASYMMETRIC =
            "R2q3n/1P6/2B5/3K4/4p3/5b2/6k1/N2Q3r";
    private static int passed;
    private static int failed;
    private static int assertions;

    private PositionTest() {
    }

    public static void main(String[] args) {
        run("empty board and metadata", PositionTest::emptyBoard);
        run("starting board and orientation", PositionTest::startingBoard);
        run("placement defaults do not infer castling", PositionTest::placementDefaults);
        run("factories and piece snapshots are independent", PositionTest::independentBoards);
        run("copy preserves all state without sharing placement", PositionTest::copyIsIndependent);
        run("editing allows exactly the piece alphabet", PositionTest::pieceAlphabet);
        run("all square names and index bounds", PositionTest::squareNamesAndBounds);
        run("canonical FEN examples round trip", PositionTest::fenRoundTrips);
        run("ASCII whitespace separates exactly six fields", PositionTest::fenWhitespace);
        run("malformed FEN field counts are rejected", PositionTest::invalidFieldCounts);
        run("placement rank structure and alphabet are strict", PositionTest::invalidPlacements);
        run("fromPlacement accepts only one exact field", PositionTest::placementIsSingleField);
        run("active color is strict", PositionTest::invalidActiveColors);
        run("all canonical castling subsets are accepted", PositionTest::canonicalCastling);
        run("duplicate and noncanonical castling are rejected", PositionTest::invalidCastling);
        run("en-passant files and side-dependent ranks", PositionTest::enPassantSyntax);
        run("counter bounds, signs, alphabet and overflow", PositionTest::counterSyntax);
        run("incomplete boards parse for later diagnostics", PositionTest::parseIsNotLegalityCheck);
        run("exactly one king of each color is required", PositionTest::kingCounts);
        run("king adjacency covers every direction without wrapping", PositionTest::adjacentKings);
        run("pawns on either back rank are reported", PositionTest::backRankPawns);
        run("piece and pawn limits for both colors", PositionTest::pieceLimits);
        run("promoted material is not rejected by piece type alone", PositionTest::promotedMaterial);
        run("each castling claim requires its own king and rook", PositionTest::castlingConsistency);
        run("en-passant needs the correct pawn and an empty target", PositionTest::enPassantBoard);
        run("en-passant origin and clocks must match the prior move", PositionTest::enPassantHistory);
        run("halfmove clock respects elapsed plies without overflow", PositionTest::elapsedPlies);
        run("invalid public metadata produces diagnostics safely", PositionTest::invalidPublicMetadata);
        run("serialization rejects malformed public metadata", PositionTest::invalidMetadataSerialization);
        run("validation and serialization preserve supplied history", PositionTest::historyIsNotInvented);
        run("clockwise rotations match four hand-checked layouts", PositionTest::clockwiseExamples);
        run("180-degree rotation matches an independent layout", PositionTest::halfTurnExample);
        run("starting position has known rotated layouts", PositionTest::startingRotations);
        run("all 64 squares map to independent rotation tables", PositionTest::everySquareRotates);
        run("rotation clears only castling and en-passant metadata", PositionTest::rotationMetadata);

        System.out.println("PositionTest: " + passed + " passed, " + failed
                + " failed (" + assertions + " assertions)");
        if (failed != 0) {
            throw new AssertionError(failed + " test groups failed");
        }
    }

    private static void emptyBoard() {
        Position p = Position.empty();
        equal(EMPTY, p.placement());
        equal(EMPTY + " w - - 0 1", p.toFen());
        equal("................................................................", new String(p.pieces()));
        equal('w', p.activeColor);
        equal("-", p.castling);
        equal("-", p.enPassant);
        equal(0, p.halfmove);
        equal(1, p.fullmove);
    }

    private static void startingBoard() {
        Position p = Position.starting();
        equal(START + " w KQkq - 0 1", p.toFen());
        equal("rnbqkbnrpppppppp................................PPPPPPPPRNBQKBNR",
                new String(p.pieces()));
        equal('r', p.pieceAt(0));
        equal('k', p.pieceAt(4));
        equal('P', p.pieceAt(48));
        equal('K', p.pieceAt(60));
        equal('R', p.pieceAt(63));
        noIssues(p);
    }

    private static void placementDefaults() {
        equal(START + " w - - 0 1", Position.fromPlacement(START).toFen());
        equal(HOME + " w - - 0 1", Position.fromPlacement(HOME).toFen());
        equal(KINGS + " w - - 0 1", Position.fromPlacement(KINGS).toFen());
    }

    private static void independentBoards() {
        Position a = Position.empty();
        Position b = Position.empty();
        a.setPiece(0, 'Q');
        equal('.', b.pieceAt(0));
        char[] snapshot = a.pieces();
        snapshot[0] = 'x';
        equal('Q', a.pieceAt(0));
        a.setPiece(1, 'p');
        equal('.', snapshot[1]);
        equal("Qp6/8/8/8/8/8/8/8", a.placement());
        Position start = Position.starting();
        start.setPiece(0, '.');
        equal(START, Position.starting().placement());
        Position placed = Position.fromPlacement(KINGS);
        placed.setPiece(4, '.');
        equal(KINGS, Position.fromPlacement(KINGS).placement());
        Position parsed = Position.parse(START + " w KQkq - 0 1");
        parsed.setPiece(63, '.');
        equal(START, Position.parse(START + " w KQkq - 0 1").placement());
    }

    private static void copyIsIndependent() {
        String fen = "4k3/8/8/8/4P3/8/8/4K3 b Kq e3 0 27";
        Position original = Position.parse(fen);
        Position copy = original.copy();
        check(original != copy, "copy must be a distinct object");
        equal(fen, copy.toFen());
        copy.setPiece(36, '.');
        copy.activeColor = 'w';
        copy.castling = "-";
        copy.enPassant = "-";
        copy.halfmove = 19;
        copy.fullmove = 42;
        equal(fen, original.toFen());
        original.setPiece(4, '.');
        equal('k', copy.pieceAt(4));
        original.activeColor = 'x';
        original.castling = null;
        original.enPassant = null;
        original.halfmove = -3;
        original.fullmove = 0;
        Position invalidCopy = original.copy();
        equal('x', invalidCopy.activeColor);
        equal(null, invalidCopy.castling);
        equal(null, invalidCopy.enPassant);
        equal(-3, invalidCopy.halfmove);
        equal(0, invalidCopy.fullmove);
        equal(original.placement(), invalidCopy.placement());
    }

    private static void pieceAlphabet() {
        Position p = Position.empty();
        String allowed = ".PNBRQKpnbrqk";
        for (int i = 0; i < allowed.length(); i++) {
            char piece = allowed.charAt(i);
            p.setPiece(28, piece);
            equal(piece, p.pieceAt(28));
        }
        p.setPiece(28, '.');
        equal(EMPTY, p.placement());
        for (int value = 0; value < 128; value++) {
            final char piece = (char) value;
            if (allowed.indexOf(piece) < 0) {
                expect(IllegalArgumentException.class, () -> p.setPiece(28, piece));
            }
        }
        for (char piece : new char[] {'\u2654', '\uff30', '\u00a0', '\uffff'}) {
            expect(IllegalArgumentException.class, () -> p.setPiece(28, piece));
        }
        equal(EMPTY, p.placement());
    }

    private static void squareNamesAndBounds() {
        String[] names = ("a8 b8 c8 d8 e8 f8 g8 h8 a7 b7 c7 d7 e7 f7 g7 h7 "
                + "a6 b6 c6 d6 e6 f6 g6 h6 a5 b5 c5 d5 e5 f5 g5 h5 "
                + "a4 b4 c4 d4 e4 f4 g4 h4 a3 b3 c3 d3 e3 f3 g3 h3 "
                + "a2 b2 c2 d2 e2 f2 g2 h2 a1 b1 c1 d1 e1 f1 g1 h1").split(" ");
        for (int i = 0; i < 64; i++) {
            equal(names[i], Position.squareName(i));
        }
        Position p = Position.empty();
        for (int index : new int[] {-1, 64, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            expect(IndexOutOfBoundsException.class, () -> Position.squareName(index));
            expect(IndexOutOfBoundsException.class, () -> p.pieceAt(index));
            expect(IndexOutOfBoundsException.class, () -> p.setPiece(index, 'K'));
        }
        equal(EMPTY, p.placement());
    }

    private static void fenRoundTrips() {
        String[] fens = {
                START + " w KQkq - 0 1",
                EMPTY + " b - - 0 1",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                "8/2k5/8/8/8/5K2/8/8 b - - 37 100",
                KINGS + " w - - 2147483647 2147483647"
        };
        for (String fen : fens) {
            Position p = Position.parse(fen);
            equal(fen, p.toFen());
            equal(fen, Position.parse(p.toFen()).toFen());
            equal(fen.substring(0, fen.indexOf(' ')), p.placement());
        }
        Position p = Position.parse(KINGS + " b Qq - 17 23");
        equal('b', p.activeColor);
        equal("Qq", p.castling);
        equal("-", p.enPassant);
        equal(17, p.halfmove);
        equal(23, p.fullmove);
        equal(KINGS + " w - - 0 1", Position.parse(KINGS + " w - - 000 001").toFen());
    }

    private static void fenWhitespace() {
        equal(KINGS + " w - - 0 1",
                Position.parse(" \t" + KINGS + "\tw  -\n-\r0\f1\u000b ").toFen());
        expect(IllegalArgumentException.class, () -> Position.parse("\u0000" + KINGS + " w - - 0 1"));
        expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - - 0 1\u0000"));
        expect(IllegalArgumentException.class, () -> Position.parse(KINGS + "\u00a0w - - 0 1"));
    }

    private static void invalidFieldCounts() {
        String[] bad = {null, "", " \t\n", KINGS, KINGS + " w", KINGS + " w - -",
                KINGS + " w - - 0", KINGS + " w - - 0 1 extra", KINGS + " w - - 0 1 2 3",
                KINGS + "  - - 0 1"};
        for (String fen : bad) {
            expect(IllegalArgumentException.class, () -> Position.parse(fen));
        }
    }

    private static void invalidPlacements() {
        String[] bad = {
                "", "8/8/8/8/8/8/8", "8/8/8/8/8/8/8/8/8", "/8/8/8/8/8/8/8",
                "8/8/8/8/8/8/8/", "8/8/8//8/8/8/8", "7/8/8/8/8/8/8/8",
                "4K4/8/8/8/8/8/8/8", "4K2/8/8/8/8/8/8/8",
                "KKKKKKKKK/8/8/8/8/8/8/8", "0/8/8/8/8/8/8/8", "9/8/8/8/8/8/8/8",
                "08/8/8/8/8/8/8/8", "44/8/8/8/8/8/8/8", "11111111/8/8/8/8/8/8/8",
                "4.3/8/8/8/8/8/8/8", "4x3/8/8/8/8/8/8/8", "4A3/8/8/8/8/8/8/8",
                "4-3/8/8/8/8/8/8/8", "\u26547/8/8/8/8/8/8/8",
                "\uff18/8/8/8/8/8/8/8", "\u0668/8/8/8/8/8/8/8",
                "8/8/8/8/8/8/8/8\u0000"
        };
        for (String placement : bad) {
            expect(IllegalArgumentException.class, () -> Position.fromPlacement(placement));
            expect(IllegalArgumentException.class, () -> Position.parse(placement + " w - - 0 1"));
        }
    }

    private static void placementIsSingleField() {
        for (String placement : new String[] {null, " " + KINGS, KINGS + " ", KINGS + "\n",
                KINGS + " w", KINGS + " w - - 0 1", KINGS + "\t0", "4 k3/8/8/8/8/8/8/4K3"}) {
            expect(IllegalArgumentException.class, () -> Position.fromPlacement(placement));
        }
    }

    private static void invalidActiveColors() {
        for (String color : new String[] {"W", "B", "wb", "-", "x", "0", "\uff57"}) {
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " " + color + " - - 0 1"));
        }
    }

    private static void canonicalCastling() {
        for (String rights : new String[] {"-", "K", "Q", "k", "q", "KQ", "Kk", "Kq",
                "Qk", "Qq", "kq", "KQk", "KQq", "Kkq", "Qkq", "KQkq"}) {
            Position p = Position.parse(HOME + " w " + rights + " - 0 1");
            equal(rights, p.castling);
            equal(HOME + " w " + rights + " - 0 1", p.toFen());
            noIssues(p);
        }
    }

    private static void invalidCastling() {
        for (String rights : new String[] {"KK", "QQ", "kk", "qq", "KQK", "KQkqK", "QK",
                "qk", "kK", "Kqk", "KQqk", "-K", "K-", "--", "A", "a", "0", "KqQ"}) {
            expect(IllegalArgumentException.class, () -> Position.parse(HOME + " w " + rights + " - 0 1"));
        }
    }

    private static void enPassantSyntax() {
        for (String square : new String[] {"a6", "b6", "c6", "d6", "e6", "f6", "g6", "h6"}) {
            equal(square, Position.parse(KINGS + " w - " + square + " 0 2").enPassant);
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " b - " + square + " 0 2"));
        }
        for (String square : new String[] {"a3", "b3", "c3", "d3", "e3", "f3", "g3", "h3"}) {
            equal(square, Position.parse(KINGS + " b - " + square + " 0 2").enPassant);
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - " + square + " 0 2"));
        }
        for (String square : new String[] {"a0", "a1", "a2", "a4", "a5", "a7", "a8", "a9",
                "i3", "A3", "a33", "aa3", "a", "3", "--", "none", "e\u0663"}) {
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - " + square + " 0 2"));
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " b - " + square + " 0 2"));
        }
    }

    private static void counterSyntax() {
        for (String value : new String[] {"-1", "-0", "+0", "+1", "1.0", "0x1", "1e2", "x",
                "\u0661", "\uff11", "2147483648", "4294967296", "9999999999999999999999999999"}) {
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - - " + value + " 1"));
            expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - - 0 " + value));
        }
        expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - - 0 0"));
        expect(IllegalArgumentException.class, () -> Position.parse(KINGS + " w - - 0 000"));
        Position p = Position.parse(KINGS + " b - - 2147483647 2147483647");
        equal(Integer.MAX_VALUE, p.halfmove);
        equal(Integer.MAX_VALUE, p.fullmove);
        equal(KINGS + " b - - 2147483647 2147483647", p.toFen());
    }

    private static void parseIsNotLegalityCheck() {
        Position p = Position.parse(EMPTY + " w KQkq d6 5 1");
        equal(EMPTY + " w KQkq d6 5 1", p.toFen());
        hasIssue(p, "king");
        hasIssue(p, "castling");
        hasIssue(p, "en-passant");
        // Checking attack maps and reachability is deliberately outside this core.
        noIssues(Position.fromPlacement("4k3/8/8/8/8/8/8/K3R3"));
    }

    private static void kingCounts() {
        hasIssue(Position.empty(), "white", "king", "0");
        hasIssue(Position.empty(), "black", "king", "0");
        Position p = Position.fromPlacement("4k3/8/8/8/8/8/8/3KK3");
        hasIssue(p, "white", "king", "2");
        p = Position.fromPlacement("3kk3/8/8/8/8/8/8/4K3");
        hasIssue(p, "black", "king", "2");
        noIssues(Position.fromPlacement(KINGS));
    }

    private static void adjacentKings() {
        // White king on e4, black king on each of the eight neighboring squares.
        for (int black : new int[] {27, 28, 29, 35, 37, 43, 44, 45}) {
            Position p = Position.empty();
            p.setPiece(36, 'K');
            p.setPiece(black, 'k');
            hasIssue(p, "king", "adjacent");
        }
        Position p = Position.empty();
        p.setPiece(39, 'K'); // h4 and a3 are not neighbors across the array boundary.
        p.setPiece(40, 'k');
        noIssues(p);
        p = Position.empty();
        p.setPiece(36, 'K');
        p.setPiece(20, 'k'); // e4 and e6 have a rank between them.
        noIssues(p);
    }

    private static void backRankPawns() {
        for (int index : new int[] {0, 7, 56, 63}) {
            for (char pawn : new char[] {'P', 'p'}) {
                Position p = Position.fromPlacement(KINGS);
                p.setPiece(index, pawn);
                hasIssue(p, "pawn", "rank", Position.squareName(index));
            }
        }
        noIssues(Position.fromPlacement("4k3/P6p/8/8/8/8/p6P/4K3"));
    }

    private static void pieceLimits() {
        hasIssue(Position.fromPlacement("4k3/8/8/8/8/P7/PPPPPPPP/4K3"), "white", "pawn", "8");
        hasIssue(Position.fromPlacement("4k3/pppppppp/p7/8/8/8/8/4K3"), "black", "pawn", "8");
        hasIssue(Position.fromPlacement("4k3/8/8/8/8/QQQQQQQQ/PPPPPPPP/4K3"), "white", "piece", "16");
        hasIssue(Position.fromPlacement("4k3/pppppppp/qqqqqqqq/8/8/8/8/4K3"), "black", "piece", "16");
        noIssues(Position.starting());
    }

    private static void promotedMaterial() {
        noIssues(Position.fromPlacement("4k3/8/8/8/8/QQ6/PPPPPPP1/4K3"));
        noIssues(Position.fromPlacement("4k3/ppppppp1/qq6/8/8/8/8/4K3"));
    }

    private static void castlingConsistency() {
        String[] rights = {"K", "Q", "k", "q"};
        int[] rookIndices = {63, 56, 7, 0};
        int[] kingIndices = {60, 60, 4, 4};
        String[] rookSquares = {"h1", "a1", "h8", "a8"};
        String[] kingSquares = {"e1", "e1", "e8", "e8"};
        for (int i = 0; i < rights.length; i++) {
            Position p = Position.parse(HOME + " w " + rights[i] + " - 0 1");
            noIssues(p);
            p.setPiece(rookIndices[i], '.');
            hasIssue(p, "castling", "rook", rookSquares[i]);
            p.setPiece(rookIndices[i], i < 2 ? 'r' : 'R');
            hasIssue(p, "castling", "rook", rookSquares[i]);
            p.setPiece(rookIndices[i], i < 2 ? 'R' : 'r');
            p.setPiece(kingIndices[i], '.');
            p.setPiece(kingIndices[i] - 1, i < 2 ? 'K' : 'k');
            hasIssue(p, "castling", "king", kingSquares[i]);
            p.castling = "-";
            noIssues(p);
        }
        noIssues(Position.starting()); // Pieces between king and rook do not remove rights.
    }

    private static void enPassantBoard() {
        String[] fens = {"4k3/8/8/3p4/8/8/8/4K3 w - d6 0 2",
                "4k3/8/8/8/4P3/8/8/4K3 b - e3 0 12"};
        int[] targets = {19, 44};
        int[] pawns = {27, 36};
        String[] pawnSquares = {"d5", "e4"};
        for (int i = 0; i < fens.length; i++) {
            Position p = Position.parse(fens[i]);
            noIssues(p); // A neighboring pawn able to capture is not required by FEN.
            p.setPiece(targets[i], 'N');
            hasIssue(p, "en-passant", p.enPassant, "empty");
            p.setPiece(targets[i], '.');
            for (char wrong : new char[] {'.', 'N', i == 0 ? 'P' : 'p'}) {
                p.setPiece(pawns[i], wrong);
                hasIssue(p, "en-passant", "pawn", pawnSquares[i]);
            }
        }
    }

    private static void enPassantHistory() {
        Position p = Position.parse("4k3/8/8/3p4/8/8/8/4K3 w - d6 0 2");
        p.setPiece(11, 'n');
        hasIssue(p, "en-passant", "d7", "empty");
        p.setPiece(11, '.');
        p.halfmove = 1;
        hasIssue(p, "en-passant", "halfmove");
        p.halfmove = 0;
        p.fullmove = 1;
        hasIssue(p, "en-passant", "fullmove");
        p = Position.parse("4k3/8/8/8/4P3/8/8/4K3 b - e3 0 1");
        noIssues(p);
        p.setPiece(52, 'B');
        hasIssue(p, "en-passant", "e2", "empty");
    }

    private static void elapsedPlies() {
        noIssues(Position.parse(KINGS + " w - - 2 2"));
        noIssues(Position.parse(KINGS + " b - - 3 2"));
        hasIssue(Position.parse(KINGS + " w - - 3 2"), "halfmove");
        hasIssue(Position.parse(KINGS + " b - - 4 2"), "halfmove");
        noIssues(Position.parse(KINGS + " w - - 2147483647 2147483647"));
        noIssues(Position.parse(KINGS + " b - - 2147483647 1073741824"));
        hasIssue(Position.parse(KINGS + " w - - 2147483647 1073741824"), "halfmove");
    }

    private static void invalidPublicMetadata() {
        Position p = Position.fromPlacement(KINGS);
        p.activeColor = 'x';
        p.castling = "QK";
        p.enPassant = "z9";
        p.halfmove = -1;
        p.fullmove = 0;
        hasIssue(p, "active", "color");
        hasIssue(p, "castling");
        hasIssue(p, "en-passant");
        hasIssue(p, "halfmove");
        hasIssue(p, "fullmove");
        p.castling = null;
        p.enPassant = null;
        hasIssue(p, "castling");
        hasIssue(p, "en-passant");
        p = Position.fromPlacement(KINGS);
        p.enPassant = "e3";
        hasIssue(p, "en-passant", "rank");
        p.castling = "";
        p.enPassant = "";
        hasIssue(p, "castling");
        hasIssue(p, "en-passant");
    }

    private static void invalidMetadataSerialization() {
        Position p = Position.fromPlacement(KINGS);
        p.activeColor = 'W';
        expect(IllegalArgumentException.class, p::toFen);
        p.activeColor = 'w';
        for (String rights : new String[] {null, "", "KK", "QK", "garbage"}) {
            p.castling = rights;
            expect(IllegalArgumentException.class, p::toFen);
        }
        p.castling = "-";
        for (String square : new String[] {null, "", "e3", "e4", "A6"}) {
            p.enPassant = square;
            expect(IllegalArgumentException.class, p::toFen);
        }
        p.enPassant = "-";
        p.halfmove = -1;
        expect(IllegalArgumentException.class, p::toFen);
        p.halfmove = 0;
        p.fullmove = 0;
        expect(IllegalArgumentException.class, p::toFen);
        p.fullmove = -1;
        expect(IllegalArgumentException.class, p::toFen);
    }

    private static void historyIsNotInvented() {
        String fen = HOME + " w Kq d6 5 9";
        Position p = Position.parse(fen);
        List<String> issues = p.validationIssues();
        check(!issues.isEmpty(), "inconsistent en-passant history must be diagnosed");
        equal(fen, p.toFen());
        issues.clear();
        check(!p.validationIssues().isEmpty(), "diagnostics must be a fresh snapshot");
        p.setPiece(63, '.');
        equal("Kq", p.castling);
        equal("d6", p.enPassant);
        equal(5, p.halfmove);
        equal(9, p.fullmove);
        hasIssue(p, "castling", "rook", "h1");
        equal("Kq", p.castling);
        equal("d6", p.enPassant);
        Position noHistory = Position.fromPlacement(HOME);
        noIssues(noHistory);
        equal("-", noHistory.castling);
        equal("-", noHistory.enPassant);
    }

    private static void clockwiseExamples() {
        Position p = Position.fromPlacement(ASYMMETRIC);
        // Each literal is independently read from the board, not generated by a rotation helper.
        String[] expected = {
                "N6R/6P1/5B2/Q3K2q/3p4/2b5/1k6/r6n",
                "r3Q2N/1k6/2b5/3p4/4K3/5B2/6P1/n3q2R",
                "n6r/6k1/5b2/4p3/q2K3Q/2B5/1P6/R6N",
                ASYMMETRIC
        };
        for (String placement : expected) {
            p.rotateClockwise();
            equal(placement, p.placement());
            equal(placement + " w - - 0 1", p.toFen());
        }
    }

    private static void halfTurnExample() {
        Position p = Position.fromPlacement(ASYMMETRIC);
        p.rotate180();
        equal("r3Q2N/1k6/2b5/3p4/4K3/5B2/6P1/n3q2R", p.placement());
        p.rotate180();
        equal(ASYMMETRIC, p.placement());
    }

    private static void startingRotations() {
        Position p = Position.starting();
        p.rotateClockwise();
        equal("RP4pr/NP4pn/BP4pb/QP4pq/KP4pk/BP4pb/NP4pn/RP4pr", p.placement());
        p = Position.starting();
        p.rotate180();
        equal("RNBKQBNR/PPPPPPPP/8/8/8/8/pppppppp/rnbkqbnr", p.placement());
    }

    private static void everySquareRotates() {
        int[] clockwise = {
                7, 15, 23, 31, 39, 47, 55, 63,
                6, 14, 22, 30, 38, 46, 54, 62,
                5, 13, 21, 29, 37, 45, 53, 61,
                4, 12, 20, 28, 36, 44, 52, 60,
                3, 11, 19, 27, 35, 43, 51, 59,
                2, 10, 18, 26, 34, 42, 50, 58,
                1, 9, 17, 25, 33, 41, 49, 57,
                0, 8, 16, 24, 32, 40, 48, 56
        };
        int[] halfTurn = {
                63, 62, 61, 60, 59, 58, 57, 56,
                55, 54, 53, 52, 51, 50, 49, 48,
                47, 46, 45, 44, 43, 42, 41, 40,
                39, 38, 37, 36, 35, 34, 33, 32,
                31, 30, 29, 28, 27, 26, 25, 24,
                23, 22, 21, 20, 19, 18, 17, 16,
                15, 14, 13, 12, 11, 10, 9, 8,
                7, 6, 5, 4, 3, 2, 1, 0
        };
        for (int source = 0; source < 64; source++) {
            Position quarter = Position.empty();
            Position half = Position.empty();
            quarter.setPiece(source, 'N');
            half.setPiece(source, 'p');
            quarter.rotateClockwise();
            half.rotate180();
            for (int target = 0; target < 64; target++) {
                equal(target == clockwise[source] ? 'N' : '.', quarter.pieceAt(target));
                equal(target == halfTurn[source] ? 'p' : '.', half.pieceAt(target));
            }
        }
    }

    private static void rotationMetadata() {
        for (boolean clockwise : new boolean[] {true, false}) {
            for (String placement : new String[] {ASYMMETRIC, EMPTY}) {
                Position p = Position.parse(placement + " b Kq e3 19 42");
                char[] before = p.pieces();
                if (clockwise) {
                    p.rotateClockwise();
                } else {
                    p.rotate180();
                }
                equal('b', p.activeColor);
                equal("-", p.castling);
                equal("-", p.enPassant);
                equal(19, p.halfmove);
                equal(42, p.fullmove);
                equal(placement.equals(EMPTY) ? '.' : 'R', before[0]);
                if (placement.equals(EMPTY)) {
                    equal(EMPTY + " b - - 19 42", p.toFen());
                }
            }
        }
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            passed++;
        } catch (AssertionError | RuntimeException failure) {
            failed++;
            System.err.println("FAIL " + name + ": " + failure);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual) {
        check(expected == null ? actual == null : expected.equals(actual),
                "expected <" + expected + "> but was <" + actual + ">");
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) {
                return;
            }
            throw new AssertionError("expected " + type.getSimpleName() + ", got " + actual, actual);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " to be thrown");
    }

    private static void noIssues(Position p) {
        List<String> issues = p.validationIssues();
        check(issues.isEmpty(), "unexpected issues: " + issues);
    }

    private static void hasIssue(Position p, String... fragments) {
        List<String> issues = p.validationIssues();
        for (String issue : issues) {
            boolean matches = true;
            String normalized = issue.toLowerCase(Locale.ROOT);
            for (String fragment : fragments) {
                if (!normalized.contains(fragment.toLowerCase(Locale.ROOT))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                check(true, "matching diagnostic");
                return;
            }
        }
        throw new AssertionError("missing issue containing " + String.join(", ", fragments)
                + "; actual: " + issues);
    }
}
