import { useEffect, useRef } from 'react'
import { createChart, IChartApi, CandlestickData, UTCTimestamp } from 'lightweight-charts'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

interface Props {
  symbol: string
}

export function StockChart({ symbol }: Props) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartApi = useRef<IChartApi | null>(null)

  useEffect(() => {
    if (!chartRef.current) return

    const chart = createChart(chartRef.current, {
      width: chartRef.current.clientWidth,
      height: 400,
      layout: { background: { color: '#1a1a2e' }, textColor: '#eee' },
      grid: { vertLines: { color: '#2a2a4a' }, horzLines: { color: '#2a2a4a' } }
    })
    chartApi.current = chart

    const candleSeries = chart.addCandlestickSeries({
      upColor: '#ff3b5c',
      downColor: '#2979ff',
      borderVisible: false,
      wickUpColor: '#ff3b5c',
      wickDownColor: '#2979ff'
    })

    // WebSocket 실시간 연결
    const stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        stompClient.subscribe(`/topic/market/${symbol}`, (msg) => {
          const tick = JSON.parse(msg.body)
          const bar: CandlestickData = {
            time: (new Date(tick.time).getTime() / 1000) as UTCTimestamp,
            open:  parseFloat(tick.price),
            high:  parseFloat(tick.price),
            low:   parseFloat(tick.price),
            close: parseFloat(tick.price)
          }
          candleSeries.update(bar)
        })
      }
    })
    stompClient.activate()

    return () => {
      stompClient.deactivate()
      chart.remove()
    }
  }, [symbol])

  return (
    <div style={{ background: '#1a1a2e', padding: '16px', borderRadius: '8px' }}>
      <h3 style={{ color: '#eee', marginBottom: '12px' }}>{symbol} 실시간 시세</h3>
      <div ref={chartRef} style={{ width: '100%' }} />
    </div>
  )
}
