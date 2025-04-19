// pages/api/generate.js
import { readFile, writeFile, unlink } from 'fs/promises';
import path from 'path';
import { PDFDocument } from 'pdf-lib';
import nodemailer from 'nodemailer';
import formidable from 'formidable';
import { IncomingForm } from 'formidable';

export const config = {
  api: {
    bodyParser: false,
  },
};

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).send('Method Not Allowed');

  const form = new IncomingForm({ multiples: false, uploadDir: '/tmp', keepExtensions: true });

  form.parse(req, async (err, fields, files) => {
    if (err) return res.status(500).json({ error: 'Form parsing error' });

    try {
      const pdfPath = path.resolve('./public/formular.pdf');
      const existingPdfBytes = await readFile(pdfPath);

      const pdfDoc = await PDFDocument.load(existingPdfBytes);
      const form = pdfDoc.getForm();

      if (form.getTextField('nume')) form.getTextField('nume').setText(fields.nume);
      if (form.getTextField('prenume')) form.getTextField('prenume').setText(fields.prenume);
      if (form.getTextField('email')) form.getTextField('email').setText(fields.email);

      if (files.semnatura) {
        const sigImageBytes = await readFile(files.semnatura[0].filepath);
        const sigImage = await pdfDoc.embedPng(sigImageBytes);
        const page = pdfDoc.getPages()[0];
        page.drawImage(sigImage, {
          x: 400,
          y: 100,
          width: 100,
          height: 50,
        });
      }

      const pdfBytes = await pdfDoc.save();
      const pdfFilePath = `/tmp/formular_completat.pdf`;
      await writeFile(pdfFilePath, pdfBytes);

      const transporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
          user: '230@eufoniastore.ro',
          pass: 'A=c9uw#_-o,1',
        },
      });

      await transporter.sendMail({
        from: '230@eufoniastore.ro',
        to: '230@eufoniastore.ro',
        subject: 'Formular 230 completat',
        text: 'Formularul 230 completat este atașat.',
        attachments: [
          {
            filename: 'formular_completat.pdf',
            path: pdfFilePath,
          },
        ],
      });

      const base64 = Buffer.from(pdfBytes).toString('base64');
      await unlink(pdfFilePath);
      return res.status(200).json({ base64 });

    } catch (e) {
      console.error(e);
      res.status(500).json({ error: 'Server error' });
    }
  });
}