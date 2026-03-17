import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

function App() {
  const [trades, setTrades] = useState([]);
  const [orderBook, setOrderBook] = useState({ bids: [], asks: [] });
  const [form, setForm] = useState({ userId: 'enes_user', symbol: 'BTC_USDT', side: 'BUY', price: '', quantity: '' });

  // Tahtanın (Order Book) anlık görüntüsünü çek
  const fetchOrderBook = async () => {
    try {
      const response = await axios.get('http://localhost:8080/api/book');
      setOrderBook(response.data);
    } catch (error) {
      console.error("Tahta çekilirken hata:", error);
    }
  };

  useEffect(() => {
    fetchOrderBook();
    const interval = setInterval(fetchOrderBook, 1000);

    // Daha güvenilir ve modern WebSocket bağlantısı
    const stompClient = new Client({
      brokerURL: 'ws://localhost:8080/ws-engine/websocket', // Direkt ws protokolü
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Motor telsizine bağlandık! 🚀');
        stompClient.subscribe('/topic/trades', (message) => {
          const newTrade = JSON.parse(message.body);
          setTrades((prev) => [newTrade, ...prev]);
          fetchOrderBook(); 
        });
      },
      onStompError: (frame) => {
        console.error('Telsiz hatası: ', frame.headers['message']);
      }
    });
    stompClient.activate();

    return () => {
      stompClient.deactivate();
      clearInterval(interval);
    };
  }, []);

  const sendOrder = async (e) => {
    e.preventDefault();
    try {
      await axios.post('http://localhost:8080/api/orders', form);
      fetchOrderBook(); // Emir gidince tahtayı hemen güncelle
    } catch (err) {
      console.error("Hata:", err);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#121212', color: 'white', minHeight: '100vh' }}>
      <h1>YTU Matching Engine | Canlı İşlem Tahtası</h1>
      
      <div style={{ display: 'flex', gap: '20px' }}>
        
        {/* SOL KOLON: EMİR DEFTERİ (ORDER BOOK) */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Emir Defteri (Order Book)</h3>
          
          {/* Satıcılar (Kırmızı - Üstte) */}
          <div style={{ marginBottom: '20px' }}>
            <table width="100%" style={{ textAlign: 'left' }}>
              <thead><tr style={{ color: '#848e9c' }}><th>Fiyat (Satış)</th><th>Miktar</th></tr></thead>
              <tbody>
                {orderBook.asks.slice(0, 10).reverse().map((ask, i) => (
                  <tr key={`ask-${i}`} style={{ color: '#f6465d' }}>
                    <td>{ask.price}</td>
                    <td>{ask.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <hr style={{ borderColor: '#2b3139', margin: '15px 0' }} />

          {/* Alıcılar (Yeşil - Altta) */}
          <div>
            <table width="100%" style={{ textAlign: 'left' }}>
              <thead><tr style={{ color: '#848e9c' }}><th>Fiyat (Alış)</th><th>Miktar</th></tr></thead>
              <tbody>
                {orderBook.bids.slice(0, 10).map((bid, i) => (
                  <tr key={`bid-${i}`} style={{ color: '#2ebd85' }}>
                    <td>{bid.price}</td>
                    <td>{bid.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* ORTA KOLON: EMİR GÖNDERME FORMU */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Yeni Emir Gönder</h3>
          <form onSubmit={sendOrder} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <select value={form.side} onChange={e => setForm({...form, side: e.target.value})} style={{padding: '8px'}}>
              <option value="BUY">AL (BUY)</option>
              <option value="SELL">SAT (SELL)</option>
            </select>
            <input type="number" step="0.01" placeholder="Fiyat" value={form.price} onChange={e => setForm({...form, price: e.target.value})} style={{padding: '8px'}} required />
            <input type="number" step="0.01" placeholder="Miktar" value={form.quantity} onChange={e => setForm({...form, quantity: e.target.value})} style={{padding: '8px'}} required />
            <button type="submit" style={{ padding: '10px', backgroundColor: form.side === 'BUY' ? '#2ebd85' : '#f6465d', color: 'white', border: 'none', cursor: 'pointer', fontWeight: 'bold' }}>
              EMRİ ATEŞLE
            </button>
          </form>
        </div>

        {/* SAĞ KOLON: GERÇEKLEŞEN İŞLEMLER */}
        <div style={{ flex: 1, backgroundColor: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
          <h3>Gerçekleşen Son İşlemler</h3>
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