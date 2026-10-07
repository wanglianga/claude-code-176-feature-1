<template>
  <div class="page-container">
    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="孩子的视力健康档案：现场首次筛查若受设备、佩戴或光线影响，学校会先复测再决定是否通知您；复测结论与两次数据均会同步在此。" />

    <el-card v-if="d.name" style="margin-bottom:12px">
      <el-descriptions :column="5" border size="small">
        <el-descriptions-item label="孩子姓名">{{ d.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ d.gender }}</el-descriptions-item>
        <el-descriptions-item label="学校">{{ d.schoolName }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ d.grade }}{{ d.className }}</el-descriptions-item>
        <el-descriptions-item label="当前状态"><el-tag type="warning">{{ d.stageLabel }}</el-tag></el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="11">
        <el-card>
          <template #header><span class="card-header-title">健康管理时间线（家长可见）</span></template>
          <el-timeline class="tl">
            <el-timeline-item v-for="e in events" :key="e.id" :timestamp="fmt(e.eventTime)" placement="top">
              <el-tag size="small" :type="tagType(e.type)" style="margin-right:6px">{{ e.typeLabel }}</el-tag>
              <span style="font-size:13px">{{ e.content }}</span>
              <div class="muted">{{ e.actor }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>

      <el-col :span="13">
        <el-card>
          <el-tabs v-model="tab">
            <el-tab-pane label="筛查与复测数据" name="screen">
              <el-timeline>
                <el-timeline-item v-for="s in d.screens" :key="s.id" :timestamp="s.screenDate" placement="top">
                  <el-tag size="small" :type="s.screenRound==='RETEST'?'warning':'primary'" style="margin-right:6px">{{ s.screenRoundLabel }}</el-tag>
                  <el-tag size="small" :type="s.abnormal?'danger':'success'" style="margin-right:6px">{{ s.abnormal?'异常':'正常' }}</el-tag>
                  <span style="font-size:13px">裸眼视力 {{ s.nakedVisionLeft||'—' }}/{{ s.nakedVisionRight||'—' }}，球镜 {{ s.sphereLeft||'—' }}/{{ s.sphereRight||'—' }}</span>
                  <div class="muted" style="font-size:12px">设备 {{ s.deviceCalibNo||'—' }} ｜ 场地 {{ s.venue||'—' }} ｜ 操作人 {{ s.operatorName||'—' }}</div>
                </el-timeline-item>
              </el-timeline>
            </el-tab-pane>

            <el-tab-pane label="复测结论说明" name="retest">
              <div v-for="r in d.retests" :key="r.id" style="margin-bottom:10px">
                <el-card shadow="never">
                  <el-tag size="small" :type="r.conclusion==='NORMAL'?'success':r.conclusion==='ABNORMAL'?'danger':'warning'">{{ r.statusLabel }}</el-tag>
                  <el-tag size="small" type="info" style="margin-left:6px">复测原因：{{ r.reasonLabel }}</el-tag>
                  <div class="muted" style="font-size:12px;margin:4px 0">{{ r.reasonDetail }}</div>
                  <div style="font-size:13px;line-height:1.7">{{ r.conclusionNote || '复测结果待录入' }}</div>
                  <div class="muted" style="font-size:12px;margin-top:4px" v-if="r.retestScreen">
                    复测设备 {{ r.retestScreen.deviceCalibNo }} ｜ 复测操作人 {{ r.retestOperatorName }} ｜ 结论人 {{ r.concludedBy }}
                  </div>
                </el-card>
              </div>
              <el-empty v-if="!d.retests?.length" description="暂无复测记录（首次筛查结果明确时无需复测）" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane label="复诊提醒与通知" name="referral">
              <el-alert type="success" :closable="false" style="margin-bottom:8px"
                title="复诊提醒一旦发出即永久保留；若复测确认系设备/光线等误判，学校会关闭任务并说明原因，不会无痕撤回您收到的通知。" />
              <el-timeline v-if="d.referrals?.length">
                <el-timeline-item v-for="t in d.referrals" :key="t.id" placement="top">
                  <el-tag size="small" :type="t.status==='VISITED'?'success':t.status==='CLOSED'?'info':t.status==='CONFIRMED'?'primary':'warning'">{{ t.statusLabel }}</el-tag>
                  <div style="font-size:13px;margin-top:2px">
                    <template v-if="t.notified">已于 {{ fmt(t.notifyTime) }} 通过{{ t.notifyChannel }}向您发出复诊提醒</template>
                    <span v-else class="muted">暂未发出复诊提醒（学校复测确认后才会通知）</span>
                  </div>
                  <div v-if="t.closeNote" style="font-size:12px;color:#67c23a;margin-top:2px">关闭说明：{{ t.closeNote }}</div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无复诊提醒" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'

const d = ref({})
const tab = ref('screen')
const events = computed(() => [...(d.value.events || [])].reverse())
const fmt = (t) => t ? t.replace('T', ' ').substring(0, 16) : ''
const tagType = (t) => ({
  SCREEN: 'primary', RETEST: 'warning', VISIT: 'success', PRESCRIPTION: 'success',
  SUBSIDY: 'warning', NOTIFY: '', PARENT_CONFIRM: '', SCHOOL_REVIEW: '',
  GLASSES: '', REVIEW: '', RESOLVE: 'success', INTERVENTION: 'info',
  IMPORT: '', STAGE_CHANGE: 'info', ISSUE: 'danger'
}[t] || 'info')

onMounted(async () => {
  try {
    d.value = await api.myChild()
  } catch (e) { /* 拦截器已提示 */ }
})
</script>

<style scoped>
.tl { max-height: 640px; overflow-y: auto; padding-right: 6px; }
</style>
