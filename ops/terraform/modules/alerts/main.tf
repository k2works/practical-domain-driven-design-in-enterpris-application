# 運用の警告（ADR-023）。SNS のトピックに集め、購読はメールを既定にする。チャットは購読を足すだけで連携する（アプリを変えない）。
# 閾値は非機能要件の OBS-01・02・10（提案の値）。アプリが出す警告は、ログの決まった印をメトリクスフィルターで数える（W10-1 計画 CP-2）。

locals {
  topics = {
    operations = var.operations_alert_emails
    data       = var.data_alert_emails
    tracking   = var.tracking_alert_emails
  }

  subscriptions = merge([
    for topic, emails in local.topics : {
      for email in emails : "${topic}/${email}" => { topic = topic, email = email }
    }
  ]...)

  # キーはアプリの OperationsAlert の code と一致させる（OperationsAlertTerraformTest が確かめる）。
  log_alerts = {
    "event-backlog-warning"  = { topics = ["operations"], description = "OBS-03 警告: 発行から 5 分以上の未完了のイベント配信がある（RB-04）" }
    "event-backlog-critical" = { topics = ["operations"], description = "OBS-03 重大: 未完了のイベント配信が 10 件以上か 30 分以上（RB-04）" }
    "poison-event"           = { topics = ["operations"], description = "処理できないイベントを捨てた。監査の欠落などを確かめる" }
    "milestone-rejected"     = { topics = ["tracking"], description = "主要実績を拒否した（有人案件を確かめる）" }
    "ingestion-missing"      = { topics = ["data", "tracking"], description = "FRS-04: 当日の外部原本の取込がない" }
  }
}

resource "aws_sns_topic" "this" {
  for_each = local.topics

  name = "${var.name}-alerts-${each.key}"
}

resource "aws_sns_topic_subscription" "email" {
  for_each = local.subscriptions

  topic_arn = aws_sns_topic.this[each.value.topic].arn
  protocol  = "email"
  endpoint  = each.value.email
}

# --- アプリのログの印 ---

resource "aws_cloudwatch_log_metric_filter" "alert" {
  for_each = local.log_alerts

  name           = "${var.name}-${each.key}"
  log_group_name = var.log_group_name
  pattern        = "\"[ALERT:${each.key}]\""

  metric_transformation {
    name      = each.key
    namespace = "${var.name}/alerts"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "log_alert" {
  for_each = local.log_alerts

  alarm_name          = "${var.name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = "${var.name}/alerts"
  metric_name         = each.key
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [for topic in each.value.topics : aws_sns_topic.this[topic].arn]

  depends_on = [aws_cloudwatch_log_metric_filter.alert]
}

# --- OBS-01: 画面の応答時間 p95（5 分間） ---

resource "aws_cloudwatch_metric_alarm" "response_time" {
  for_each = { warning = 1.5, critical = 3 }

  alarm_name          = "${var.name}-obs01-response-time-${each.key}"
  alarm_description   = "OBS-01 ${each.key}: 応答時間の p95 が ${each.value} 秒超"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  dimensions          = { LoadBalancer = var.load_balancer_arn_suffix }
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 1
  threshold           = each.value
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.this["operations"].arn]
}

# --- OBS-02: 5xx の割合（5 分間） ---

resource "aws_cloudwatch_metric_alarm" "server_errors" {
  for_each = { warning = 1, critical = 5 }

  alarm_name          = "${var.name}-obs02-5xx-rate-${each.key}"
  alarm_description   = "OBS-02 ${each.key}: 5xx の割合が ${each.value}% 超"
  evaluation_periods  = 1
  threshold           = each.value
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.this["operations"].arn]

  # アプリの 5xx と、ALB 自身が返す 5xx（タスクが応答できないとき）の両方を数える
  metric_query {
    id          = "rate"
    expression  = "IF(requests > 0, 100 * (FILL(target_errors, 0) + FILL(elb_errors, 0)) / requests, 0)"
    return_data = true
  }

  metric_query {
    id = "requests"

    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "RequestCount"
      dimensions  = { LoadBalancer = var.load_balancer_arn_suffix }
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "target_errors"

    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_Target_5XX_Count"
      dimensions  = { LoadBalancer = var.load_balancer_arn_suffix }
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "elb_errors"

    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_ELB_5XX_Count"
      dimensions  = { LoadBalancer = var.load_balancer_arn_suffix }
      period      = 300
      stat        = "Sum"
    }
  }
}

# --- OBS-10: ヘルスチェック ---

resource "aws_cloudwatch_metric_alarm" "unhealthy_task" {
  alarm_name          = "${var.name}-obs10-health-warning"
  alarm_description   = "OBS-10 警告: ヘルスチェックに失敗するタスクがある"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  dimensions          = { LoadBalancer = var.load_balancer_arn_suffix, TargetGroup = var.target_group_arn_suffix }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.this["operations"].arn]
}

resource "aws_cloudwatch_metric_alarm" "no_healthy_task" {
  alarm_name          = "${var.name}-obs10-health-critical"
  alarm_description   = "OBS-10 重大: ヘルスチェックに通るタスクがない"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = { LoadBalancer = var.load_balancer_arn_suffix, TargetGroup = var.target_group_arn_suffix }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.this["operations"].arn]
}
