package com.bew1769.niagaragit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.stream.XMLStreamException;

/** Textconv views for .bog and .dist files. Output depends only on archive bytes. */
public final class Convert {
    private Convert() {
    }

    public static String bog(byte[] data) {
        if (BogStore.isStore(data)) {
            try {
                return bogZip(BogStore.smudge(data));
            } catch (IOException e) {
                return opaque("bog", data, "store format could not be rebuilt");
            }
        }
        if (ZipArchives.looksLikeZip(data)) {
            return bogZip(data);
        }
        if (XmlFmt.looksLikeXml(data)) {
            return renderXml(data);
        }
        return opaque("bog", data, "not a zip archive or XML document; passphrase-protected bog files are encrypted");
    }

    public static String dist(byte[] data) {
        if (ZipArchives.looksLikeZip(data)) {
            return distZip(data);
        }
        if (XmlFmt.looksLikeXml(data)) {
            return renderXml(data);
        }
        return opaque("dist", data, "not a zip archive; passphrase-protected distribution files are encrypted");
    }

    private static String bogZip(byte[] data) {
        ZipArchives.Archive archive;
        try {
            archive = ZipArchives.read(data);
        } catch (IOException e) {
            return opaque("bog", data, "not a readable zip archive: File is not a zip file");
        }
        return renderMembers(archive.entries);
    }

    private static String distZip(byte[] data) {
        ZipArchives.Archive archive;
        try {
            archive = ZipArchives.read(data);
        } catch (IOException e) {
            return opaque("dist", data, "not a readable zip archive: File is not a zip file");
        }
        return renderDist(archive.entries);
    }

    private static String renderDist(List<ZipArchives.Member> members) {
        List<Shown> shown = new ArrayList<Shown>();
        List<Sized> secrets = new ArrayList<Sized>();
        List<Other> others = new ArrayList<Other>();
        Map<String, long[]> noisy = new TreeMap<String, long[]>();
        List<BogDoc> bogs = new ArrayList<BogDoc>();
        List<ZipArchives.Member> ordered = new ArrayList<ZipArchives.Member>(members);
        Collections.sort(ordered, new Comparator<ZipArchives.Member>() {
            public int compare(ZipArchives.Member a, ZipArchives.Member b) {
                return sortKeyLess(a.name, b.name);
            }
        });
        for (int i = 0; i < ordered.size(); i++) {
            ZipArchives.Member member = ordered.get(i);
            if (member.directory) {
                continue;
            }
            Classifier.Result classified = Classifier.classify(member.name);
            if (classified.kind == Kind.NOISY) {
                String cat = Classifier.noisyCategory(classified.display);
                long[] cur = noisy.get(cat);
                if (cur == null) {
                    cur = new long[2];
                    noisy.put(cat, cur);
                }
                cur[0]++;
                cur[1] += member.declaredSize;
                continue;
            }
            if (classified.kind == Kind.SECRET) {
                secrets.add(new Sized(classified.display, member.declaredSize));
                continue;
            }
            if (classified.kind == Kind.UNSAFE) {
                others.add(new Other("unsafe", classified.display, member.declaredSize));
                continue;
            }
            if (member.declaredSize > NiagaraGit.MAX_ENTRY_BYTES || "ValueError".equals(member.unreadable)) {
                others.add(new Other("too-large", classified.display, member.declaredSize));
                continue;
            }
            if (member.unreadable != null || member.data == null) {
                String why = member.unreadable == null ? "BadZipFile" : member.unreadable;
                others.add(new Other("unreadable (" + why + ")", classified.display, member.declaredSize));
                continue;
            }
            shown.add(new Shown(classified.display, member.data.length, Digests.sha256(member.data)));
            if (Classifier.fold(classified.display).endsWith(".bog")) {
                String xml = trimRightNewlines(bog(member.data));
                bogs.add(new BogDoc(classified.display, xml));
            }
        }
        List<String> lines = new ArrayList<String>();
        lines.add("# niagara-git dist format " + NiagaraGit.FORMAT_VERSION);
        lines.add("# files " + shown.size());
        lines.add("# skipped-noisy " + noisyCount(noisy));
        lines.add("# skipped-secret " + secrets.size());
        if (!others.isEmpty()) {
            lines.add("# skipped-other " + others.size());
        }
        lines.add("");
        lines.add("## files");
        Collections.sort(shown, new Comparator<Shown>() {
            public int compare(Shown a, Shown b) {
                return pathLess(a.path, b.path);
            }
        });
        for (int i = 0; i < shown.size(); i++) {
            Shown file = shown.get(i);
            lines.add(file.size + " " + file.digest + " " + file.path);
        }
        if (!noisy.isEmpty()) {
            lines.add("");
            lines.add("## skipped-noisy");
            for (Map.Entry<String, long[]> e : noisy.entrySet()) {
                lines.add(e.getKey() + " count=" + e.getValue()[0] + " size=" + e.getValue()[1]);
            }
        }
        if (!secrets.isEmpty()) {
            lines.add("");
            lines.add("## skipped-secret");
            Collections.sort(secrets, new Comparator<Sized>() {
                public int compare(Sized a, Sized b) {
                    return pathLess(a.path, b.path);
                }
            });
            for (int i = 0; i < secrets.size(); i++) {
                Sized file = secrets.get(i);
                lines.add(file.size + " " + file.path);
            }
        }
        if (!others.isEmpty()) {
            lines.add("");
            lines.add("## skipped-other");
            Collections.sort(others, new Comparator<Other>() {
                public int compare(Other a, Other b) {
                    return pathLess(a.path, b.path);
                }
            });
            for (int i = 0; i < others.size(); i++) {
                Other file = others.get(i);
                lines.add(file.reason + " " + file.size + " " + file.path);
            }
        }
        Collections.sort(bogs, new Comparator<BogDoc>() {
            public int compare(BogDoc a, BogDoc b) {
                return pathLess(a.path, b.path);
            }
        });
        for (int i = 0; i < bogs.size(); i++) {
            BogDoc doc = bogs.get(i);
            lines.add("");
            lines.add("## bog " + doc.path);
            lines.add(doc.xml);
        }
        return join(lines) + "\n";
    }

    private static String renderMembers(List<ZipArchives.Member> members) {
        List<XmlDoc> rendered = new ArrayList<XmlDoc>();
        List<Shown> binaries = new ArrayList<Shown>();
        List<String> problems = new ArrayList<String>();
        List<ZipArchives.Member> ordered = new ArrayList<ZipArchives.Member>(members);
        Collections.sort(ordered, new Comparator<ZipArchives.Member>() {
            public int compare(ZipArchives.Member a, ZipArchives.Member b) {
                return sortKeyLess(a.name, b.name);
            }
        });
        for (int i = 0; i < ordered.size(); i++) {
            ZipArchives.Member member = ordered.get(i);
            if (member.directory) {
                continue;
            }
            Classifier.Result classified = Classifier.classify(member.name);
            if (classified.kind != Kind.SHOW) {
                problems.add("# skipped " + classified.kind + " " + classified.display);
                continue;
            }
            if (member.declaredSize > NiagaraGit.MAX_ENTRY_BYTES || "ValueError".equals(member.unreadable)) {
                problems.add("# skipped too-large " + member.declaredSize + " " + classified.display);
                continue;
            }
            if (member.unreadable != null || member.data == null) {
                String why = member.unreadable == null ? "BadZipFile" : member.unreadable;
                problems.add("# unreadable " + classified.display + " (" + why + ")");
                continue;
            }
            if (isXmlMember(classified.display, member.data)) {
                rendered.add(new XmlDoc(classified.display, trimRightNewlines(renderXml(member.data))));
            } else {
                binaries.add(new Shown(classified.display, member.data.length, Digests.sha256(member.data)));
            }
        }
        if (rendered.size() == 1 && binaries.isEmpty() && problems.isEmpty()) {
            return rendered.get(0).xml + "\n";
        }
        List<String> lines = new ArrayList<String>();
        if (rendered.isEmpty() && binaries.isEmpty() && problems.isEmpty()) {
            lines.add("# niagara-git: bog zip contains no file entries");
        }
        for (int i = 0; i < binaries.size(); i++) {
            Shown file = binaries.get(i);
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("# binary " + file.path);
            lines.add("# size " + file.size);
            lines.add("# sha256 " + file.digest);
        }
        for (int i = 0; i < rendered.size(); i++) {
            XmlDoc doc = rendered.get(i);
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("# entry " + doc.path);
            lines.add(doc.xml);
        }
        for (int i = 0; i < problems.size(); i++) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add(problems.get(i));
        }
        return join(lines) + "\n";
    }

    private static String renderXml(byte[] data) {
        try {
            return XmlFmt.pretty(data);
        } catch (XMLStreamException e) {
            String message = e.getMessage() == null ? "XML parse error" : e.getMessage();
            if (!message.startsWith("XML parse error") && !message.startsWith("DTD")) {
                message = "XML parse error: " + message;
            }
            return XmlFmt.normalizedSource(data, message);
        }
    }

    private static boolean isXmlMember(String display, byte[] payload) {
        if (Classifier.fold(display).endsWith(".xml")) {
            return true;
        }
        return XmlFmt.looksLikeXml(payload);
    }

    private static String opaque(String kind, byte[] data, String reason) {
        return "# niagara-git: " + kind + " not decoded (" + reason + ")\n# size " + data.length
                + "\n# sha256 " + Digests.sha256(data) + "\n";
    }

    private static int sortKeyLess(String a, String b) {
        String as = a.replace('\\', '/');
        String bs = b.replace('\\', '/');
        String af = Classifier.fold(as);
        String bf = Classifier.fold(bs);
        int c = af.compareTo(bf);
        if (c != 0) {
            return c;
        }
        return as.compareTo(bs);
    }

    private static int pathLess(String a, String b) {
        int c = Classifier.fold(a).compareTo(Classifier.fold(b));
        if (c != 0) {
            return c;
        }
        return a.compareTo(b);
    }

    private static long noisyCount(Map<String, long[]> noisy) {
        long n = 0;
        for (long[] cur : noisy.values()) {
            n += cur[0];
        }
        return n;
    }

    private static String trimRightNewlines(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(0, end);
    }

    private static String join(List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append(lines.get(i));
        }
        return b.toString();
    }

    private static final class Shown {
        final String path;
        final int size;
        final String digest;

        Shown(String path, int size, String digest) {
            this.path = path;
            this.size = size;
            this.digest = digest;
        }
    }

    private static final class Sized {
        final String path;
        final long size;

        Sized(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }

    private static final class Other {
        final String reason;
        final String path;
        final long size;

        Other(String reason, String path, long size) {
            this.reason = reason;
            this.path = path;
            this.size = size;
        }
    }

    private static final class BogDoc {
        final String path;
        final String xml;

        BogDoc(String path, String xml) {
            this.path = path;
            this.xml = xml;
        }
    }

    private static final class XmlDoc {
        final String path;
        final String xml;

        XmlDoc(String path, String xml) {
            this.path = path;
            this.xml = xml;
        }
    }
}
