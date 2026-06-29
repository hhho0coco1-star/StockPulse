import { useState } from 'react'
import { StockChart } from './components/StockChart'

const SYMBOLS = [
  { code: '005930', name: '삼성전자' },
  { code: '000660', name: 'SK하이닉스' }
]

export default function App() {
  const [selected, setSelected] = useState('005930')

  return (
    <div style={{ minHeight: '100vh', background: '#0f0f23', color: '#eee', padding: '24px', fontFamily: 'sans-serif' }}>
      <h1 style={{ color: '#fff', marginBottom: '24px' }}>📈 StockPulse</h1>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
        {SYMBOLS.map(s => (
          <button
            key={s.code}
            onClick={() => setSelected(s.code)}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: 'none',
              background: selected === s.code ? '#ff3b5c' : '#2a2a4a',
              color: '#fff',
              cursor: 'pointer'
            }}
          >
            {s.name} ({s.code})
          </button>
        ))}
      </div>

      <StockChart symbol={selected} />
    </div>
  )
}
