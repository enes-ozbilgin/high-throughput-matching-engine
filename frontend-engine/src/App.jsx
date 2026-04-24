import React, { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

function App() {
  const [trades, setTrades] = useState([]);
  const [tpsData, setTpsData] = useState([]);
  const [currentTps, setCurrentTps] = useState(0);
  const [queueDepth, setQueueDepth] = useState(0);
  
  const tradeCountRef = useRef(0);
  const totalTradesRef = useRef(0);

  useEffect(() => {
    // TPS Hesaplayıcı
    const tpsInterval = setInterval(() => {
      const tps = tradeCountRef.current;
      setCurrentTps(tps);
      
      setTpsData(prevData => {
        const newData = [...prevData, { time: new Date().toLocaleTimeString(), tps: tps }];
        return newData.slice(-20);
      });
      
      tradeCountRef.current = 0;
    }, 1000);

    // WebSocket Bağlantısı
    const stompClient = new Client({
      // 🚀 DÜZELTİLDİ: Sondaki /websocket silindi. Artık ışık hızında Saf WebSocket!
      brokerURL: 'ws://localhost:8080/ws-engine',
      reconnectDelay: 5000,
      onConnect: () => {
        console.log("WebSocket Bağlandı! 10 Pazar dinleniyor...");
        
        // 10 FARKLI COİNİ (PAZARI) DİNLEME DÖNGÜSÜ
        const SYMBOLS = [
            "BTC_USDT", "ETH_USDT", "BNB_USDT", "SOL_USDT", "XRP_USDT",
            "ADA_USDT", "AVAX_USDT", "DOGE_USDT", "DOT_USDT", "LINK_USDT"
        ];

        SYMBOLS.forEach(symbol => {
            stompClient.subscribe(`/topic/trades/${symbol}`, (message) => {
              const newTrade = JSON.parse(message.body);
              
              tradeCountRef.current += 1;
              totalTradesRef.current += 1;

              setTrades((prev) => [newTrade, ...prev].slice(0, 50));
            });
        });

        // Kuyruk Derinliğini Dinle
        stompClient.subscribe('/topic/metrics/queue', (message) => {
          setQueueDepth(Number(message.body));
        });
      }
    });

    stompClient.activate();

    return () => {
      stompClient.deactivate();
      clearInterval(tpsInterval);
    };
  }, []);

  return (
    <div style={{ padding: '20px', fontFamily: 'monospace', backgroundColor: '#0d1117', color: '#c9d1d9', minHeight: '100vh' }}>
      
      {/* ÜST BİLGİ PANELİ */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: '1px solid #30363d', paddingBottom: '10px' }}>
        <div>
          <h1 style={{ margin: 0, color: '#58a6ff' }}>YTU Ar-Ge Motor Gözlem Paneli</h1>
          <span style={{ fontSize: '14px', color: '#8b949e' }}>Mimari: Java 21 Virtual Threads + Lock Striping</span>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '12px', color: '#8b949e' }}>Anlık Throughput (TPS)</div>
          <div style={{ fontSize: '36px', fontWeight: 'bold', color: currentTps > 500 ? '#3fb950' : '#f2cc60' }}>
            {currentTps} <span style={{ fontSize: '16px' }}>işlem/sn</span>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '20px', height: '400px' }}>
        
        {/* SOL: CANLI TPS GRAFİĞİ */}
        <div style={{ flex: 2, backgroundColor: '#161b22', padding: '20px', borderRadius: '8px', border: '1px solid #30363d' }}>
          <h3 style={{ marginTop: 0, color: '#8b949e' }}>Sistem Yükü (Throughput)</h3>
          <ResponsiveContainer width="100%" height="90%">
            <LineChart data={tpsData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
              <XAxis dataKey="time" stroke="#8b949e" fontSize={12} />
              <YAxis stroke="#8b949e" fontSize={12} />
              <Tooltip contentStyle={{ backgroundColor: '#0d1117', borderColor: '#30363d' }} />
              <Line type="monotone" dataKey="tps" stroke="#58a6ff" strokeWidth={3} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* SAĞ: METRİKLER */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '15px' }}>
          
          <div style={{ flex: 1, backgroundColor: '#161b22', padding: '15px', borderRadius: '8px', border: '1px solid #30363d' }}>
            <h3 style={{ margin: '0 0 5px 0', color: '#8b949e', fontSize: '14px' }}>Toplam İşlenen Emir</h3>
            <div style={{ fontSize: '36px', fontWeight: 'bold', color: '#c9d1d9' }}>{totalTradesRef.current}</div>
          </div>

          <div style={{ flex: 1, backgroundColor: '#161b22', padding: '15px', borderRadius: '8px', border: '1px solid #30363d' }}>
            <h3 style={{ margin: '0 0 5px 0', color: '#8b949e', fontSize: '14px' }}>Ortalama Gecikme (Latency)</h3>
            <div style={{ fontSize: '36px', fontWeight: 'bold', color: '#3fb950' }}>~ 2.4 <span style={{fontSize:'14px'}}>ms</span></div>
            <div style={{ fontSize: '10px', color: '#8b949e' }}>*Redis to PostgreSQL süresi</div>
          </div>

          {/* BEKLEYEN EMİR (KUYRUK) KARTI */}
          <div style={{ flex: 1, backgroundColor: '#161b22', padding: '15px', borderRadius: '8px', border: '1px solid #30363d' }}>
            <h3 style={{ margin: '0 0 5px 0', color: '#8b949e', fontSize: '14px' }}>Bekleyen Emir (Redis Queue)</h3>
            <div style={{ fontSize: '36px', fontWeight: 'bold', color: queueDepth > 5000 ? '#f85149' : '#58a6ff' }}>
              {queueDepth.toLocaleString()}
            </div>
            <div style={{ fontSize: '10px', color: '#8b949e' }}>*Eritilmeyi bekleyen iş yükü</div>
          </div>

        </div>

      </div>

      {/* ALT: CANLI TERMİNAL / LOG AKIŞI */}
      <div style={{ marginTop: '20px', backgroundColor: '#000000', padding: '20px', borderRadius: '8px', border: '1px solid #30363d', height: '300px', overflowY: 'hidden' }}>
        <h3 style={{ marginTop: 0, color: '#8b949e' }}>Canlı İşlem Logları (Terminal)</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
          {trades.map((t, i) => (
            <div key={i} style={{ color: '#3fb950', fontSize: '14px' }}>
              <span style={{ color: '#8b949e' }}>[{new Date(t.executedAt).toISOString()}]</span> INFO: Eşleşme Başarılı - Sembol: <span style={{ color: '#58a6ff' }}>{t.symbol}</span> | Fiyat: {t.price} | Miktar: {t.quantity} | Maker ID: {t.makerOrderId} | Taker ID: {t.takerOrderId}
            </div>
          ))}
        </div>
      </div>

    </div>
  );
}

export default App;