import { useState } from 'react';

export default function Home() {
  const [loading, setLoading] = useState(false);
  const [pdfUrl, setPdfUrl] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    const formData = new FormData(e.target);

    const res = await fetch('/api/generate', {
      method: 'POST',
      body: formData,
    });

    const data = await res.json();
    if (data.base64) {
      const blob = await fetch(`data:application/pdf;base64,${data.base64}`).then(r => r.blob());
      const url = URL.createObjectURL(blob);
      setPdfUrl(url);
    }

    setLoading(false);
  };

  return (
    <div style={{ fontFamily: 'Arial', padding: 30 }}>
      <h1>Formular 230</h1>
      <form onSubmit={handleSubmit}>
        <input name="nume" placeholder="Nume" required /><br />
        <input name="prenume" placeholder="Prenume" required /><br />
        <input name="email" type="email" placeholder="Email" required /><br />
        <input name="semnatura" type="file" accept="image/png" required /><br />
        <button type="submit" disabled={loading}>
          {loading ? 'Se trimite...' : 'Trimite'}
        </button>
      </form>
      {pdfUrl && (
        <p>
          <a href={pdfUrl} download="formular_230.pdf">Descarcă PDF completat</a>
        </p>
      )}
    </div>
  );
}