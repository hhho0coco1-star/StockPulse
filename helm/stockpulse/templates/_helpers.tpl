{{/*
공통 라벨
*/}}
{{- define "stockpulse.labels" -}}
app.kubernetes.io/part-of: stockpulse
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/*
서비스별 셀렉터 라벨
*/}}
{{- define "stockpulse.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .release }}
{{- end }}

{{/*
이미지 참조 (registry/name:tag)
*/}}
{{- define "stockpulse.image" -}}
{{ .registry }}/{{ .name }}:{{ .tag }}
{{- end }}

{{/*
HPA enabled 여부 체크
*/}}
{{- define "stockpulse.hpaEnabled" -}}
{{- $svcHpa := .svc.hpa | default dict -}}
{{- $defaultHpa := .defaults.hpa | default dict -}}
{{- $enabled := $svcHpa.enabled | default $defaultHpa.enabled | default false -}}
{{- $enabled -}}
{{- end }}
