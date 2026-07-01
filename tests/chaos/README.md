# StockPulse Chaos Mesh 장애 주입

## 전제 조건

- Kubernetes 클러스터 + 네임스페이스 `stockpulse` (Phase 8 배포 후)
- Chaos Mesh 설치:
  ```bash
  helm repo add chaos-mesh https://charts.chaos-mesh.org
  helm install chaos-mesh chaos-mesh/chaos-mesh -n chaos-mesh --create-namespace
  kubectl get pods -n chaos-mesh
  ```

## 로컬 환경 대체 검증 (K8s 미구성 시)

| 시나리오 | Chaos Mesh | docker-compose 대체 |
|---------|-----------|-------------------|
| Pod kill | pod-kill.yaml | `docker stop stockpulse-trading-service-1` |
| Redis 지연 | network-delay.yaml | `docker exec stockpulse-redis-1 tc qdisc add dev eth0 root netem delay 200ms 20ms` |
| Kafka 격리 | kafka-partition-offline.yaml | `docker network disconnect stockpulse_stockpulse-net stockpulse-kafka-1` |

## dry-run 검증 (DoD #3)

```bash
kubectl apply --dry-run=client -f tests/chaos/pod-kill.yaml
kubectl apply --dry-run=client -f tests/chaos/network-delay.yaml
kubectl apply --dry-run=client -f tests/chaos/kafka-partition-offline.yaml
```

모두 `created (dry run)` 출력되면 PASS.

---

## 시나리오 1 — Pod kill (trading-service)

**목적**: Pod 강제 종료 → Deployment 자기치유(즉각 재기동) 확인

```bash
# 1. 주문 부하 병행 (별 터미널)
k6 run --vus 10 --duration 120s tests/load/scenarioB_order.js

# 2. Pod kill 주입
kubectl apply -n stockpulse -f tests/chaos/pod-kill.yaml

# 3. 재기동 관찰
kubectl get pods -n stockpulse -l app.kubernetes.io/name=trading-service -w

# 4. 정리
kubectl delete -f tests/chaos/pod-kill.yaml
```

**관찰 포인트**:
- Pod Terminating → Running 전환 시간 (MTTR)
- k6 에러율: 재시작 중 잠깐 5xx 후 회복 → 전체 error_rate < 5% 목표
- Grafana → StockPulse Overview → `up` 패널에서 trading-service 0→1 복귀

---

## 시나리오 2 — Network delay (market-collector → Redis)

**목적**: Redis 200ms 지연 → Resilience4j CB CLOSED→OPEN→HALF_OPEN 전이 확인

```bash
# 1. 시세 조회 부하 병행
k6 run --vus 20 --duration 150s tests/load/scenarioA_quote.js

# 2. 지연 주입
kubectl apply -n stockpulse -f tests/chaos/network-delay.yaml

# 3. CB 상태 관찰 (30초마다)
watch -n 30 "curl -s http://localhost:8082/actuator/circuitbreakers | jq '.circuitBreakers.redis.state'"

# 4. 정리 (90s 후 자동 해제 or 수동)
kubectl delete -f tests/chaos/network-delay.yaml
```

**관찰 포인트**:
- Actuator: `state: OPEN` → `HALF_OPEN` → `CLOSED`
- Grafana → CB state 패널
- CB OPEN 중 API 응답: 503 + `error.code=UPSTREAM_UNAVAILABLE`
- fallback: DB 직접 조회 경로(TickRepository.findLatestBySymbol) 로그 확인

---

## 시나리오 3 — Kafka 브로커 격리

**목적**: Kafka 단절 → DLT 적재 → 복구 후 재처리 확인

```bash
# 1. Kafka 격리 주입
kubectl apply -n stockpulse -f tests/chaos/kafka-partition-offline.yaml

# 2. 발행 실패 로그 확인
kubectl logs -n stockpulse -l app.kubernetes.io/name=trading-service --tail=50 | grep -E "Failed|retry|DLT"

# 3. 60s 후 자동 복구
# 4. DLT 메시지 확인
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic market.tick.DLT \
  --from-beginning \
  --max-messages 10

kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.events.DLT \
  --from-beginning \
  --max-messages 10

# 5. 정리
kubectl delete -f tests/chaos/kafka-partition-offline.yaml
```

**관찰 포인트**:
- 격리 중 producer 로그: `KafkaProducerException`, retry 소진 후 DLT 라우팅
- 복구 후 Grafana → Kafka lag 패널에서 consumer lag 0 회복
- DLT 메시지 ≥ 1건 확인 → DoD #5 충족

---

## 공통 teardown

```bash
kubectl delete podchaos,networkchaos --all -n stockpulse
```
