import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

function App() {
  const [orders, setOrders] = useState([]);
  const [trades, setTrades] = useState([]);
  const [form, setForm] = useState({ userId: 'enes_user', symbol: 'BTC_USDT', side: 'BUY', price: '', quantity: '' });

  // 1. WebSocket Bağlantısı (Canlı Veri İçin)
  useEffect(() => {
    const socket = new SockJS('http://localhost:8080/ws-engine');
    const stompClient = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        console.log('Motor telsizine bağlandık! 🚀');
        stompClient.subscribe('/topic/trades', (message) => {
          const newTrade = JSON.parse(message.body);
          setTrades((prev) => [newTrade, ...prev]);
        });
      },
    });
    stompClient.activate();
    return () => stompClient.deactivate();
  }, []);

  // 2. Yeni Emir Gönder (API İsteği)
  const sendOrder = async (e) => {
    e.preventDefault();
    try {
      await axios.post('http://localhost:8080/api/orders', form);
      alert("Emir başarıyla gönderildi!");
    } catch (err) {
      console.error("Hata:", err);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#121212', color: 'white', minHeight: '100vh' }}>
      <h1>YTU Matching Engine | Canlı İşlem Tahtası</h1>
      
      <div style={{ display: 'flex', gap: '40px' }}>
        {/* SOL TARAF: EMİR GÖNDERME FORMU */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Yeni Emir Gönder</h3>
          <form onSubmit={sendOrder} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <select value={form.side} onChange={e => setForm({...form, side: e.target.value})} style={{padding: '8px'}}>
              <option value="BUY">AL (BUY)</option>
              <option value="SELL">SAT (SELL)</option>
            </select>
            <input type="number" placeholder="Fiyat" value={form.price} onChange={e => setForm({...form, price: e.target.value})} style={{padding: '8px'}} />
            <input type="number" placeholder="Miktar" value={form.quantity} onChange={e => setForm({...form, quantity: e.target.value})} style={{padding: '8px'}} />
            <button type="submit" style={{ padding: '10px', backgroundColor: form.side === 'BUY' ? '#2ebd85' : '#f6465d', color: 'white', border: 'none', cursor: 'pointer' }}>
              Emri Ateşle
            </button>
          </form>
        </div>

        {/* SAĞ TARAF: CANLI İŞLEMLER (TRADE HISTORY) */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Gerçekleşen Son İşlemler (Trade History)</h3>
          <table width="100%" style={{ textAlign: 'left' }}>
            <thead>
              <tr style={{ color: '#848e9c' }}>
                <th>Fiyat</th>
                <th>Miktar</th>
                <th>Zaman</th>
              </tr>
            </thead>
            <tbody>
              {trades.map((t, i) => (
                <tr key={i}>
                  <td style={{ color: '#2ebd85' }}>{t.price}</td>
                  <td>{t.quantity}</td>
                  <td style={{ fontSize: '12px' }}>{new Date(t.executedAt).toLocaleTimeString()}</td>
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