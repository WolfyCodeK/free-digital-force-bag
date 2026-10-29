package app.notes;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.util.ArrayList;
import java.util.List;

/** Converts note formatting between stored [start, end, kind] ranges and text spans. */
final class Fmt {

    private Fmt() {}

    /** Marks heading spans so they can be told apart from plain bold. */
    static final class Heading extends RelativeSizeSpan {
        final int kind;

        Heading(int kind) {
            super(kind == Note.H1 ? 1.375f : 1.125f);
            this.kind = kind;
        }
    }

    static final class Bold extends StyleSpan {
        Bold() {
            super(Typeface.BOLD);
        }
    }

    static final class Italic extends StyleSpan {
        Italic() {
            super(Typeface.ITALIC);
        }
    }

    static final class Underline extends UnderlineSpan {
    }

    static Object span(int kind) {
        switch (kind) {
            case Note.BOLD: return new Bold();
            case Note.ITALIC: return new Italic();
            case Note.UNDERLINE: return new Underline();
            case Note.H1:
            case Note.H2: return new Heading(kind);
            default: return null;
        }
    }

    static int kind(Object span) {
        if (span instanceof Bold) return Note.BOLD;
        if (span instanceof Italic) return Note.ITALIC;
        if (span instanceof Underline) return Note.UNDERLINE;
        if (span instanceof Heading) return ((Heading) span).kind;
        return 0;
    }

    /** The note's text with its formatting applied. */
    static CharSequence styled(Note n) {
        if (n.spans.isEmpty()) return n.text;
        SpannableStringBuilder sb = new SpannableStringBuilder(n.text);
        apply(sb, n.spans);
        return sb;
    }

    static void apply(Spannable s, List<int[]> spans) {
        int len = s.length();
        for (int[] r : spans) {
            int start = Math.max(0, Math.min(len, r[0]));
            int end = Math.max(start, Math.min(len, r[1]));
            if (start == end) continue;
            Object sp = span(r[2]);
            if (sp != null) s.setSpan(sp, start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            if (r[2] == Note.H1 || r[2] == Note.H2) {
                s.setSpan(new Bold(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            }
        }
    }

    /** Reads formatting back from edited text. Bold that belongs to a heading is skipped. */
    static List<int[]> extract(Spanned s) {
        List<int[]> out = new ArrayList<>();
        List<int[]> headings = new ArrayList<>();
        for (Heading h : s.getSpans(0, s.length(), Heading.class)) {
            int a = s.getSpanStart(h), b = s.getSpanEnd(h);
            if (a < b) {
                out.add(new int[]{a, b, h.kind});
                headings.add(new int[]{a, b});
            }
        }
        for (CharacterStyle cs : s.getSpans(0, s.length(), CharacterStyle.class)) {
            int k = kind(cs);
            if (k == 0 || k == Note.H1 || k == Note.H2) continue;
            int a = s.getSpanStart(cs), b = s.getSpanEnd(cs);
            if (a >= b) continue;
            if (k == Note.BOLD) {
                boolean inHeading = false;
                for (int[] h : headings) if (a >= h[0] && b <= h[1]) inHeading = true;
                if (inHeading) continue;
            }
            out.add(new int[]{a, b, k});
        }
        return out;
    }
}
