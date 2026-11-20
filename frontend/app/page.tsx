"use client";
import { useEffect, useState } from "react";

type Price = { symbol:string; price:number; confidence:number; confidencePct:number; ageMs:number; stale:boolean; reason:string };

export default function Home() {
  const [prices, setPrices] = useState<Price[]>([]);
  const [amount, setAmount] = useState(0.01);
  const [quote, setQuote] = useState<any>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const load = () => fetch("http://localhost:8080/api/prices").then(r=>r.json()).then(setPrices);
    load(); const t = setInterval(load, 2000); return () => clearInterval(t);
  }, []);

  async function getQuote(symbol:string) {
    setError(""); setQuote(null);
    const res = await fetch("http://localhost:8080/api/quote", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body: JSON.stringify({ symbol, amount })
    });
    if(!res.ok){ setError(await res.text()); return; }
    setQuote(await res.json());
  }

  return (
    <main className="p-8 max-w-5xl mx-auto bg-black text-white min-h-screen">
      <h1 className="text-3xl font-bold">Pyth Guarded Terminal</h1>
      <p className="text-zinc-400 mt-1">Backend validates every price. Frontend never trusts Hermes directly.</p>

      <div className="grid grid-cols-3 gap-4 mt-6">
        {prices.map(p => (
          <div key={p.symbol} className={`p-4 rounded-xl border ${p.stale ? "border-red-500" : "border-zinc-700"}`}>
            <div className="flex justify-between"><b>{p.symbol}/USD</b><span className={p.stale ? "text-red-400" : "text-green-400"}>{p.reason}</span></div>
            <div className="text-2xl mt-2">${p.price.toLocaleString()}</div>
            <div className="text-xs text-zinc-400 mt-1">Conf ±${p.confidence.toFixed(2)} ({p.confidencePct.toFixed(3)}%) • Age {p.ageMs}ms</div>
            <div className="flex gap-2 mt-3">
              <input type="number" value={amount} onChange={e=>setAmount(Number(e.target.value))} className="bg-zinc-900 p-2 rounded w-24" />
              <button onClick={()=>getQuote(p.symbol)} className="bg-white text-black px-3 rounded">Get Quote</button>
            </div>
          </div>
        ))}
      </div>

      {quote && <div className="mt-4 p-4 bg-green-900/30 rounded">Quote: {quote.amount} {quote.symbol} = ${quote.total} @ ${quote.unitPrice}</div>}
      {error && <div className="mt-4 p-4 bg-red-900/40 rounded">{error}</div>}
    </main>
  );
}