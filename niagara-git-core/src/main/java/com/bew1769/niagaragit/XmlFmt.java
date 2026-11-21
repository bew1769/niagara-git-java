package com.bew1769.niagaragit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Pretty-printer for the Baja Object Graph XML stored in a .bog zip.
 * Component order is preserved. Attribute order and insignificant whitespace
 * are normalized so the same station prints the same bytes every time.
 */
final class XmlFmt {
    static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final Pattern ENCODING_DECL = Pattern.compile(
            "^<\\?xml[^>]*encoding=[\"']([A-Za-z0-9._-]+)[\"']");

    private enum NodeKind { ELEM, COMMENT, PI }

    private static final class Attr {
        final String key;
        final String val;

        Attr(String key, String val) {
            this.key = key;
            this.val = val;
        }
    }

    private static final class Node {
        final NodeKind kind;
        String tag;
        final List<Attr> attrs = new ArrayList<Attr>();
        String text = "";
        String tail = "";
        final List<Node> children = new ArrayList<Node>();

        Node(NodeKind kind) {
            this.kind = kind;
        }

        void appendText(String s) {
            if (children.isEmpty()) {
                text += s;
            } else {
                children.get(children.size() - 1).tail += s;
            }
        }
    }

    private XmlFmt() {
    }

    /**
     * The trim set matches the Go tool: a UTF-8 BOM and ASCII space.
     */
    static boolean looksLikeXml(byte[] data) {
        byte[] head = trimLeft(data);
        return startsWith(head, "<?xml") || startsWith(head, "<");
    }

    static String pretty(byte[] data) throws XMLStreamException {
        if (hasDoctype(data)) {
            throw new XMLStreamException("DTD or entity declarations are not expanded");
        }
        Node root = parse(data);
        StringBuilder b = new StringBuilder();
        b.append(XML_HEADER).append('\n');
        writeNode(b, root, 0);
        b.append('\n');
        return b.toString();
    }

    static String normalizedSource(byte[] data, String reason) {
        String text = decodeXmlBytes(data);
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        if (text.length() > 0 && !text.endsWith("\n")) {
            text = text + "\n";
        }
        return "# niagara-git: " + reason + "\n" + text;
    }

    private static boolean hasDoctype(byte[] data) {
        int n = Math.min(data.length, 8192);
        String head = new String(data, 0, n, StandardCharsets.ISO_8859_1).toLowerCase(java.util.Locale.ROOT);
        return head.contains("<!doctype") || head.contains("<!entity");
    }

    private static Node parse(byte[] data) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(prepare(data)));
        List<Node> stack = new ArrayList<Node>();
        Node root = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        Node n = new Node(NodeKind.ELEM);
                        n.tag = xmlTag(reader.getPrefix(), reader.getNamespaceURI(), reader.getLocalName());
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            String key = xmlTag(reader.getAttributePrefix(i), reader.getAttributeNamespace(i),
                                    reader.getAttributeLocalName(i));
                            n.attrs.add(new Attr(key, reader.getAttributeValue(i)));
                        }
                        if (stack.isEmpty()) {
                            if (root == null) {
                                root = n;
                            }
                        } else {
                            stack.get(stack.size() - 1).children.add(n);
                        }
                        stack.add(n);
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        if (stack.isEmpty()) {
                            throw new XMLStreamException("unexpected end element");
                        }
                        stack.remove(stack.size() - 1);
                        break;
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                    case XMLStreamConstants.SPACE:
                        if (!stack.isEmpty()) {
                            stack.get(stack.size() - 1).appendText(reader.getText());
                        }
                        break;
                    case XMLStreamConstants.COMMENT:
                        if (!stack.isEmpty()) {
                            Node c = new Node(NodeKind.COMMENT);
                            c.text = reader.getText();
                            stack.get(stack.size() - 1).children.add(c);
                        }
                        break;
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        if (stack.isEmpty()) {
                            break;
                        }
                        String target = reader.getPITarget();
                        if (target != null && target.equalsIgnoreCase("xml")) {
                            break;
                        }
                        String body = target == null ? "" : target;
                        String inst = reader.getPIData();
                        if (inst != null && inst.length() > 0) {
                            int i = 0;
                            while (i < inst.length() && inst.charAt(i) == ' ') {
                                i++;
                            }
                            body = body + " " + inst.substring(i);
                        }
                        Node p = new Node(NodeKind.PI);
                        p.text = body;
                        stack.get(stack.size() - 1).children.add(p);
                        break;
                    default:
                        break;
                }
            }
        } finally {
            reader.close();
        }
        if (root == null) {
            throw new XMLStreamException("missing root element");
        }
        return root;
    }

    private static String xmlTag(String prefix, String ns, String local) {
        if (ns != null && ns.length() > 0) {
            return "{" + ns + "}" + local;
        }
        if (prefix != null && prefix.length() > 0) {
            return prefix + ":" + local;
        }
        return local;
    }

    private static byte[] prepare(byte[] data) {
        if (startsWithBytes(data, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf})) {
            return Arrays.copyOfRange(data, 3, data.length);
        }
        if (startsWithBytes(data, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWithBytes(data, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            return decodeXmlBytes(data).getBytes(StandardCharsets.UTF_8);
        }
        return data;
    }

    private static String decodeXmlBytes(byte[] data) {
        if (startsWithBytes(data, new byte[] {(byte) 0xff, (byte) 0xfe})) {
            return decodeUtf16(Arrays.copyOfRange(data, 2, data.length), false);
        }
        if (startsWithBytes(data, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            return decodeUtf16(Arrays.copyOfRange(data, 2, data.length), true);
        }
        if (startsWithBytes(data, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf})) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        String enc = "utf-8";
        int n = Math.min(data.length, 200);
        String head = new String(data, 0, n, StandardCharsets.ISO_8859_1);
        Matcher m = ENCODING_DECL.matcher(head);
        if (m.find()) {
            enc = m.group(1).toLowerCase(java.util.Locale.ROOT);
        }
        if ("iso-8859-1".equals(enc) || "latin1".equals(enc) || "latin-1".equals(enc)) {
            return new String(data, StandardCharsets.ISO_8859_1);
        }
        if ("utf-16le".equals(enc)) {
            return decodeUtf16(data, false);
        }
        if ("utf-16be".equals(enc) || "utf-16".equals(enc)) {
            return decodeUtf16(data, true);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String decodeUtf16(byte[] data, boolean bigEndian) {
        int len = data.length - (data.length % 2);
        char[] chars = new char[len / 2];
        for (int i = 0; i < chars.length; i++) {
            int b0 = data[2 * i] & 0xff;
            int b1 = data[2 * i + 1] & 0xff;
            chars[i] = bigEndian ? (char) ((b0 << 8) | b1) : (char) (b0 | (b1 << 8));
        }
        return new String(chars);
    }

    private static void writeNode(StringBuilder b, Node n, int indent) {
        String pad = pad(indent);
        if (n.kind == NodeKind.COMMENT) {
            b.append(pad).append("<!--").append(n.text.replace("--", "- -")).append("-->");
            return;
        }
        if (n.kind == NodeKind.PI) {
            String body = n.text.replace('\r', ' ').replace('\n', ' ');
            b.append(pad).append("<?").append(body).append("?>");
            return;
        }
        b.append(pad).append('<').append(n.tag);
        List<Attr> attrs = new ArrayList<Attr>(n.attrs);
        java.util.Collections.sort(attrs, new Comparator<Attr>() {
            public int compare(Attr a, Attr c) {
                return a.key.compareTo(c.key);
            }
        });
        for (int i = 0; i < attrs.size(); i++) {
            Attr a = attrs.get(i);
            b.append(' ').append(a.key).append("=\"").append(escapeAttr(a.val)).append('"');
        }
        String text = significant(n.text);
        if (n.children.isEmpty() && text == null) {
            b.append("/>");
            return;
        }
        if (n.children.isEmpty()) {
            b.append('>').append(escapeText(text)).append("</").append(n.tag).append('>');
            return;
        }
        b.append('>');
        if (text != null) {
            b.append('\n').append(pad).append("  ").append(escapeText(text));
        }
        for (int i = 0; i < n.children.size(); i++) {
            Node child = n.children.get(i);
            b.append('\n');
            writeNode(b, child, indent + 1);
            String tail = significant(child.tail);
            if (tail != null) {
                b.append('\n').append(pad).append("  ").append(escapeText(tail));
            }
        }
        b.append('\n').append(pad).append("</").append(n.tag).append('>');
    }

    private static String significant(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = trimSpace(text);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimSpace(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isXmlSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isXmlSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isXmlSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static String escapeText(String text) {
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '\r':
                    b.append("&#13;");
                    break;
                case '\n':
                    b.append("&#10;");
                    break;
                default:
                    b.append(c);
                    break;
            }
        }
        return b.toString();
    }

    private static String escapeAttr(String text) {
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                case '\r':
                    b.append("&#13;");
                    break;
                case '\n':
                    b.append("&#10;");
                    break;
                case '\t':
                    b.append("&#9;");
                    break;
                default:
                    b.append(c);
                    break;
            }
        }
        return b.toString();
    }

    private static String pad(int indent) {
        char[] chars = new char[indent * 2];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }

    private static byte[] trimLeft(byte[] data) {
        int i = 0;
        if (startsWithBytes(data, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf})) {
            i = 3;
        }
        while (i < data.length) {
            byte c = data[i];
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                break;
            }
            i++;
        }
        if (i == 0) {
            return data;
        }
        return Arrays.copyOfRange(data, i, data.length);
    }

    private static boolean startsWith(byte[] data, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        return startsWithBytes(data, p);
    }

    private static boolean startsWithBytes(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
