import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Client } from '@stomp/stompjs';

const SYMBOLS = [
  "BTC_USDT", "ETH_USDT", "BNB_USDT", "SOL_USDT", "XRP_USDT",
  "ADA_USDT", "AVAX_USDT", "DOGE_USDT", "DOT_USDT", "LINK_USDT"
];

function App() {
  const [activeSymbol, setActiveSymbol] = useState("BTC_USDT");
  const [trades, setTrades] = useState([]);
  const [orderBook, setOrderBook] = useState({ bids: [], asks: [] });
  // type: 'LIMIT' state'e eklendi
  const [form, setForm] = useState({ userId: 'enes_user', side: 'BUY', type: 'LIMIT', price: '', quantity: '' });

  const fetchOrderBook = async (symbol) => {
    try {
      const response = await axios.get(`http://localhost:8080/api/book/${symbol}`);
      setOrderBook(response.data);
    } catch (error) { console.error("Tahta hatası:", error); }
  };

  const fetchHistoricalTrades = async (symbol) => {
    try {
      const response = await axios.get(`http://localhost:8080/api/trades/${symbol}`);
      setTrades(response.data);
    } catch (error) { console.error("Geçmiş işlem hatası:", error); }
  };

  useEffect(() => {
    // Sembol değiştiğinde ekranı temizle ve yeni verileri çek
    setTrades([]);
    fetchOrderBook(activeSymbol);
    fetchHistoricalTrades(activeSymbol);

    const interval = setInterval(() => fetchOrderBook(activeSymbol), 1000);

    const stompClient = new Client({
      brokerURL: 'ws://localhost:8080/ws-engine/websocket',
      reconnectDelay: 5000,
      onConnect: () => {
        // Sadece aktif sembolün kanalını dinle
        stompClient.subscribe(`/topic/trades/${activeSymbol}`, (message) => {
          const newTrade = JSON.parse(message.body);
          setTrades((prev) => [newTrade, ...prev]);
          fetchOrderBook(activeSymbol);
        });
      }
    });
    stompClient.activate();

    return () => {
      stompClient.deactivate();
      clearInterval(interval);
    };
  }, [activeSymbol]); 

  const sendOrder = async (e) => {
    e.preventDefault();
    try {
      // Formdaki price boşsa ve MARKET emriyse, backend'in hata vermemesi için price'ı 0 (veya null) yollayabiliriz.
      const orderPayload = { 
        ...form, 
        symbol: activeSymbol,
        price: form.type === 'MARKET' ? 0 : form.price 
      };
      await axios.post('http://localhost:8080/api/orders', orderPayload);
      fetchOrderBook(activeSymbol);
    } catch (err) { console.error("Emir gönderme hatası:", err); }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#121212', color: 'white', minHeight: '100vh' }}>
      
      {/* ÜST MENÜ: MARKET SEÇİCİ */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h1>YTU Matching Engine</h1>
        <select 
          value={activeSymbol} 
          onChange={(e) => setActiveSymbol(e.target.value)} 
          style={{ padding: '10px', fontSize: '18px', fontWeight: 'bold', backgroundColor: '#2b3139', color: '#fcd535', border: 'none', borderRadius: '5px' }}>
          {SYMBOLS.map(sym => <option key={sym} value={sym}>{sym.replace('_', '/')}</option>)}
        </select>
      </div>
      
      <div style={{ display: 'flex', gap: '20px' }}>
        {/* SOL KOLON: EMİR DEFTERİ (ORDER BOOK) */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3 style={{ color: '#fcd535' }}>{activeSymbol.replace('_', '/')} Tahtası</h3>
          {/* Satıcılar (Kırmızı) */}
          <div style={{ marginBottom: '15px' }}>
            <table width="100%" style={{ textAlign: 'left' }}>
              <thead><tr style={{ color: '#848e9c' }}><th>Fiyat</th><th>Miktar</th></tr></thead>
              <tbody>
                {orderBook.asks.slice(0, 8).reverse().map((ask, i) => (
                  <tr key={`ask-${i}`} style={{ color: '#f6465d' }}><td>{ask.price}</td><td>{ask.quantity}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
          <hr style={{ borderColor: '#2b3139', margin: '15px 0' }} />
          {/* Alıcılar (Yeşil) */}
          <div>
            <table width="100%" style={{ textAlign: 'left' }}>
              <tbody>
                {orderBook.bids.slice(0, 8).map((bid, i) => (
                  <tr key={`bid-${i}`} style={{ color: '#2ebd85' }}><td>{bid.price}</td><td>{bid.quantity}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* ORTA KOLON: EMİR FORMU */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Yeni Emir Gönder</h3>
          <form onSubmit={sendOrder} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
            
            {/* Emir Tipi Seçimi */}
            <select value={form.type} onChange={e => setForm({...form, type: e.target.value})} style={{padding: '10px', backgroundColor: '#2b3139', color: 'white', border: '1px solid #3f4751'}}>
              <option value="LIMIT">LİMİT EMRİ (Fiyat Belirle)</option>
              <option value="MARKET">PİYASA EMRİ (Anında Al/Sat)</option>
            </select>

            {/* Yön Seçimi */}
            <select value={form.side} onChange={e => setForm({...form, side: e.target.value})} style={{padding: '10px', backgroundColor: '#2b3139', color: 'white', border: '1px solid #3f4751'}}>
              <option value="BUY">AL (BUY)</option>
              <option value="SELL">SAT (SELL)</option>
            </select>
            
            {/* Fiyat Girişi (Sadece Limit seçiliyse görünür) */}
            {form.type === 'LIMIT' && (
              <input type="number" step="0.01" placeholder="Fiyat" value={form.price} onChange={e => setForm({...form, price: e.target.value})} style={{padding: '10px'}} required />
            )}
            
            <input type="number" step="0.01" placeholder="Miktar" value={form.quantity} onChange={e => setForm({...form, quantity: e.target.value})} style={{padding: '10px'}} required />
            
            <button type="submit" style={{ padding: '12px', backgroundColor: form.side === 'BUY' ? '#2ebd85' : '#f6465d', color: 'white', border: 'none', cursor: 'pointer', fontWeight: 'bold' }}>
              {form.type === 'MARKET' ? 'ANINDA ' : ''}{form.side === 'BUY' ? 'AL' : 'SAT'}
            </button>
          </form>
        </div>

        {/* SAĞ KOLON: GERÇEKLEŞEN İŞLEMLER */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Gerçekleşen İşlemler</h3>
          <table width="100%" style={{ textAlign: 'left' }}>
            <thead><tr style={{ color: '#848e9c' }}><th>Fiyat</th><th>Miktar</th><th>Zaman</th></tr></thead>
            <tbody>
              {trades.map((t, i) => (
                <tr key={i}>
                  <td style={{ color: '#2ebd85' }}>{t.price}</td>
                  <td>{t.quantity}</td>
                  <td style={{ fontSize: '12px', color: '#848e9c' }}>{new Date(t.executedAt).toLocaleTimeString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default App;