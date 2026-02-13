package com.pdftools.service;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that removes digital signatures from PDF files.
 */
@Service
public class PdfSignatureService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfSignatureService.class);

    /**
     * Opens the input PDF, strips all signature fields and their annotations,
     * removes the AcroForm SigFlags, and writes a clean PDF to the output path.
     */
    public void removeSignatures(Path inputPath, Path outputPath) throws IOException {
        try (
            PdfReader reader = new PdfReader(inputPath.toString()).setUnethicalReading(true);
            PdfWriter writer = new PdfWriter(outputPath.toString());
            PdfDocument pdfDoc = new PdfDocument(reader, writer)
        ) {
            removeSignatureFields(pdfDoc);
            removeSignatureAnnotations(pdfDoc);
            clearAcroFormSignatureFlags(pdfDoc);

            LOG.info("Processed PDF with {} page(s)", pdfDoc.getNumberOfPages());
        }
    }

    private void removeSignatureFields(PdfDocument pdfDoc) {
        PdfDictionary catalog = pdfDoc.getCatalog().getPdfObject();
        PdfDictionary acroForm = catalog.getAsDictionary(PdfName.AcroForm);

        if (acroForm == null) {
            LOG.info("No AcroForm found — document has no form fields.");
            return;
        }

        PdfArray fields = acroForm.getAsArray(PdfName.Fields);
        if (fields == null || fields.isEmpty()) {
            LOG.info("No form fields found.");
            return;
        }

        List<PdfObject> toRemove = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            PdfDictionary field = fields.getAsDictionary(i);
            if (field != null && PdfName.Sig.equals(field.getAsName(PdfName.FT))) {
                toRemove.add(fields.get(i));
                LOG.info("Found signature field to remove: {}",
                        field.getAsString(PdfName.T) != null
                                ? field.getAsString(PdfName.T).getValue()
                                : "(unnamed)");
                clearFieldValue(field);
            }
        }

        for (PdfObject obj : toRemove) {
            fields.remove(obj);
        }

        if (!toRemove.isEmpty()) {
            acroForm.put(PdfName.Fields, fields);
            acroForm.setModified();
            LOG.info("Removed {} signature field(s).", toRemove.size());
        }
    }

    private void clearFieldValue(PdfDictionary field) {
        field.remove(PdfName.V);
        field.remove(PdfName.SV);
        field.remove(PdfName.Lock);
        field.setModified();
    }

    private void removeSignatureAnnotations(PdfDocument pdfDoc) {
        int totalRemoved = 0;

        for (int pageNum = 1; pageNum <= pdfDoc.getNumberOfPages(); pageNum++) {
            List<PdfAnnotation> annotations = pdfDoc.getPage(pageNum).getAnnotations();
            List<PdfAnnotation> toRemove = new ArrayList<>();

            for (PdfAnnotation annotation : annotations) {
                if (annotation instanceof PdfWidgetAnnotation) {
                    PdfDictionary annotDict = annotation.getPdfObject();
                    PdfDictionary parent = annotDict.getAsDictionary(PdfName.Parent);

                    boolean isSigWidget = PdfName.Sig.equals(annotDict.getAsName(PdfName.FT));
                    boolean parentIsSig = parent != null
                            && PdfName.Sig.equals(parent.getAsName(PdfName.FT));

                    if (isSigWidget || parentIsSig) {
                        toRemove.add(annotation);
                    }
                }
            }

            for (PdfAnnotation annotation : toRemove) {
                pdfDoc.getPage(pageNum).removeAnnotation(annotation);
                totalRemoved++;
            }
        }

        if (totalRemoved > 0) {
            LOG.info("Removed {} signature annotation(s) from pages.", totalRemoved);
        }
    }

    private void clearAcroFormSignatureFlags(PdfDocument pdfDoc) {
        PdfDictionary catalog = pdfDoc.getCatalog().getPdfObject();
        PdfDictionary acroForm = catalog.getAsDictionary(PdfName.AcroForm);

        if (acroForm != null && acroForm.get(PdfName.SigFlags) != null) {
            acroForm.remove(PdfName.SigFlags);
            acroForm.setModified();
            LOG.info("Cleared AcroForm SigFlags.");
        }
    }
}
