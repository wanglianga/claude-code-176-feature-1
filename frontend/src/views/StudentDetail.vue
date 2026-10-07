<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" title="返回名单" :content="d.name + ' 的健康管理档案'" style="margin-bottom:12px" />

    <el-card style="margin-bottom:12px">
      <el-descriptions :column="5" border size="small">
        <el-descriptions-item label="姓名">{{ d.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ d.gender }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ d.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="学校">{{ d.schoolName }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ d.grade }}{{ d.className }}</el-descriptions-item>
        <el-descriptions-item label="家长">{{ d.parentName }} / {{ d.parentPhone }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag type="warning">{{ d.stageLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="异常标记" :span="2">
          <el-tag v-for="(f,i) in d.flagLabels" :key="i" type="danger" size="small" style="margin:2px">{{ f }}</el-tag>
          <span v-if="!d.flagLabels?.length" class="muted">无</span>
        </el-descriptions-item>
        <el-descriptions-item label="既往视力档案" :span="5">{{ d.priorVisionArchive || '—' }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-button-group>
          <el-button type="primary" plain @click="openDialog('screen')">现场筛查/补检</el-button>
          <el-button type="warning" plain @click="openDialog('retestInit')">发起二次复测</el-button>
          <el-button type="primary" plain @click="openDialog('review')">学校复核</el-button>
          <el-button type="primary" plain @click="openDialog('notify')">家长告知</el-button>
          <el-button type="primary" plain @click="openDialog('choice')">家长确认选择</el-button>
          <el-button type="success" plain @click="openDialog('visit')">复诊回写</el-button>
          <el-button type="success" plain @click="openDialog('glasses')">配镜登记</el-button>
          <el-button type="warning" plain @click="openDialog('subsidy')">补助申请</el-button>
          <el-button type="warning" plain @click="openDialog('followup')">复查登记</el-button>
          <el-button type="info" plain @click="openDialog('intervention')">学校干预</el-button>
        </el-button-group>
      </div>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="11">
        <el-card>
          <template #header><span class="card-header-title">健康管理时间线（筛查→告知→复诊→处方→配镜→补助→复查→干预）</span></template>
          <el-timeline class="tl">
            <el-timeline-item v-for="e in timelineRows" :key="e.id" :type="dotType(e.type)"
                              :timestamp="fmt(e.eventTime)" placement="top">
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
            <el-tab-pane label="筛查与复测记录" name="screen">
              <el-timeline>
                <el-timeline-item v-for="s in (d.screens?.length ? d.screens : [d.latestScreen].filter(Boolean))" :key="s.id"
                                  :timestamp="s.screenDate" placement="top">
                  <el-tag size="small" :type="s.screenRound==='RETEST'?'warning':'primary'" style="margin-right:6px">
                    {{ s.screenRoundLabel }}
                  </el-tag>
                  <el-tag size="small" :type="s.abnormal?'danger':'success'" style="margin-right:6px">
                    {{ s.absent ? '请假未检' : s.abnormal ? '异常' : '正常' }}
                  </el-tag>
                  <span style="font-size:13px">
                    裸眼视力 {{ s.nakedVisionLeft || '—' }}/{{ s.nakedVisionRight || '—' }}，
                    球镜 {{ s.sphereLeft || '—' }}/{{ s.sphereRight || '—' }}，
                    散光 {{ s.cylinderLeft || '—' }}/{{ s.cylinderRight || '—' }}
                  </span>
                  <div class="muted" style="font-size:12px;margin-top:2px">
                    设备 {{ s.deviceCalibNo || '—' }} ｜ 场地 {{ s.venue || '—' }} ｜
                    {{ s.orgName }} {{ s.operatorName }} ｜ 戴镜 {{ s.wearingGlasses ? '是' : '否' }}
                  </div>
                  <div v-if="s.abnormalReason" class="muted" style="font-size:12px;color:#f56c6c">{{ s.abnormalReason }}</div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-if="!(d.screens?.length) && !d.latestScreen" description="暂无筛查记录" :image-size="60" />

              <!-- 二次确认复测卡片 -->
              <el-divider content-position="left">
                <el-tag type="warning" effect="plain">异常二次确认（复测）</el-tag>
              </el-divider>
              <div v-for="r in (d.retests || [])" :key="r.id" style="margin-bottom:10px">
                <el-card shadow="never">
                  <div>
                    <el-tag size="small" :type="r.conclusion==='NORMAL'?'success':r.conclusion==='ABNORMAL'?'danger':'warning'">
                      {{ r.statusLabel }}
                    </el-tag>
                    <el-tag size="small" type="info" style="margin-left:6px">复测原因：{{ r.reasonLabel }}</el-tag>
                    <span class="muted" style="margin-left:8px;font-size:12px">发起：{{ r.initiatedBy }} {{ fmt(r.initiatedAt) }}</span>
                  </div>
                  <div class="muted" style="font-size:12px;margin:4px 0">{{ r.reasonDetail }}</div>
                  <el-descriptions :column="2" size="small" border style="margin-top:6px">
                    <el-descriptions-item label="首次设备/视力">
                      {{ r.originalScreen.deviceCalibNo }} ｜ {{ r.originalScreen.nakedVisionLeft }}/{{ r.originalScreen.nakedVisionRight }}
                    </el-descriptions-item>
                    <el-descriptions-item label="复测设备/视力">
                      <template v-if="r.retestScreen">
                        {{ r.retestScreen.deviceCalibNo }} ｜ {{ r.retestScreen.nakedVisionLeft }}/{{ r.retestScreen.nakedVisionRight }}
                      </template>
                      <span v-else class="muted">待录入复测数据</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="复测操作人">{{ r.retestOperatorName || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="复测场地">{{ r.retestScreen?.venue || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="复测结论（同步家长档案）" :span="2">{{ r.conclusionNote || '待复测' }}</el-descriptions-item>
                  </el-descriptions>
                </el-card>
              </div>
              <div v-if="canRetestNow" style="margin-top:8px">
                <el-button type="warning" size="small" @click="openDialog('retestInit')">在原记录上发起复测</el-button>
                <el-button v-if="pendingRetest" type="primary" size="small"
                  @click="openDialog('retestSubmit', { retestId: pendingRetest.id,
                    deviceCalibNo: d.latestScreen?.deviceCalibNo, venue: d.latestScreen?.venue })">
                  录入待办复测结果
                </el-button>
              </div>

              <!-- 转诊任务留痕 -->
              <el-divider content-position="left">
                <el-tag type="success" effect="plain">家长复诊提醒（转诊任务）</el-tag>
              </el-divider>
              <el-timeline v-if="d.referrals?.length">
                <el-timeline-item v-for="t in d.referrals" :key="t.id" placement="top">
                  <el-tag size="small" :type="t.status==='VISITED'?'success':t.status==='CLOSED'?'info':t.status==='CONFIRMED'?'primary':'warning'">
                    {{ t.statusLabel }}
                  </el-tag>
                  <span class="muted" style="margin-left:6px;font-size:12px">来源：{{ t.sourceLabel }}</span>
                  <div style="font-size:13px;margin-top:2px">
                    <template v-if="t.notified">
                      家长通知已发出（{{ t.notifyChannel }}，{{ fmt(t.notifyTime) }}）——记录保留不可删除
                    </template>
                    <span v-else class="muted">尚未通知家长</span>
                    <span v-if="t.targetOrg"> ｜ 复诊机构：{{ t.targetOrg }}</span>
                  </div>
                  <div v-if="t.closeNote" class="muted" style="font-size:12px;color:#67c23a">关闭原因：{{ t.closeNote }}</div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无转诊任务" :image-size="50" />
            </el-tab-pane>

            <el-tab-pane :label="`复诊处方(${d.visits?.length || 0})`" name="visit">
              <el-timeline>
                <el-timeline-item v-for="v in d.visits" :key="v.id" :timestamp="v.visitDate">
                  <b>{{ v.orgName }} · {{ v.doctor }}</b>
                  <div style="font-size:13px;margin:4px 0">
                    真性近视：<el-tag size="small" :type="v.trueMyopia?'danger':'success'">{{ v.trueMyopia?'是':'否' }}</el-tag>
                    需散瞳：{{ v.needMydriasis?'是':'否' }} ｜ 复查周期：{{ v.reviewCycleMonths }}个月 ｜
                    配镜补助：<el-tag size="small" :type="v.subsidyEligible?'warning':'info'">{{ v.subsidyEligible?'符合':'不符合' }}</el-tag>
                  </div>
                  <div class="muted">球镜 {{ v.rxSphereLeft }}/{{ v.rxSphereRight }}，柱镜 {{ v.rxCylinderLeft }}/{{ v.rxCylinderRight }}，轴位 {{ v.rxAxisLeft }}/{{ v.rxAxisRight }}，瞳距 {{ v.pd }}</div>
                  <div>{{ v.diagnosis }}</div>
                  <el-alert v-if="v.bigDifference" type="error" :closable="false" style="margin-top:4px"
                            :title="'与筛查差异较大：' + v.differenceNote" />
                </el-timeline-item>
              </el-timeline>
              <el-empty v-if="!d.visits?.length" description="暂无复诊记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane :label="`配镜(${d.glasses?.length || 0})`" name="glasses">
              <div v-for="g in d.glasses" :key="g.id" style="margin-bottom:10px">
                <el-card shadow="never">
                  <b>{{ g.opticianOrg }}</b>
                  <el-tag size="small" :type="g.partnerOptician?'success':'danger'" style="margin-left:8px">
                    {{ g.partnerOptician ? '合作机构' : '自行外配' }}
                  </el-tag>
                  <div class="muted">{{ g.glassesDate }} ｜ 金额 {{ g.amount }} 元 ｜ {{ g.lensInfo }}</div>
                  <el-alert v-if="g.discomfort" :type="g.discomfortResolved?'success':'error'" :closable="false"
                    :title="(g.discomfortResolved?'配镜不适已处理：':'配镜后不适：') + g.discomfortDetail" style="margin-top:4px" />
                  <el-button v-if="g.discomfort && !g.discomfortResolved" size="small" type="primary"
                             style="margin-top:6px" @click="resolveDiscomfort(g)">登记复诊调整</el-button>
                </el-card>
              </div>
              <el-empty v-if="!d.glasses?.length" description="暂无配镜记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane label="补助" name="subsidy">
              <el-descriptions v-for="a in d.subsidies" :key="a.id" :column="2" border size="small">
                <el-descriptions-item label="状态">
                  <el-tag :type="a.status==='PAID'?'success':a.status==='DISPUTE'?'danger':a.status==='REJECTED'?'info':'warning'">
                    {{ a.statusLabel }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="申请/核准">{{ a.applyAmount }} / {{ a.approvedAmount || '—' }} 元</el-descriptions-item>
                <el-descriptions-item label="申请人">{{ a.applicant }}</el-descriptions-item>
                <el-descriptions-item label="审批人">{{ a.approver || '—' }}</el-descriptions-item>
                <el-descriptions-item label="意见" :span="2">{{ a.opinion || '—' }}</el-descriptions-item>
              </el-descriptions>
              <el-empty v-if="!d.subsidies?.length" description="暂无补助记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane :label="`复查计划(${d.followups?.length || 0})`" name="followup">
              <el-table :data="d.followups" size="small" border>
                <el-table-column prop="plannedDate" label="计划日期" width="110" />
                <el-table-column prop="actualDate" label="实际日期" width="110" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status==='DONE'?'success':row.status==='NO_SHOW'?'danger':'warning'">
                      {{ {DONE:'已完成',NO_SHOW:'未到场',PLANNED:'待复查'}[row.status] }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="orgName" label="机构" width="140" />
                <el-table-column prop="visionResult" label="结果" />
                <el-table-column prop="remindCount" label="催办" width="60" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane :label="`学校干预(${d.interventions?.length || 0})`" name="intervention">
              <el-timeline>
                <el-timeline-item v-for="i in d.interventions" :key="i.id" :timestamp="i.execDate">
                  <el-tag size="small" type="info">{{ i.typeLabel }}</el-tag>
                  <span style="margin-left:6px">{{ i.content }}</span>
                  <div class="muted">{{ i.executor }}</div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-if="!d.interventions?.length" description="暂无干预记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane :label="`异常事项(${d.issues?.length || 0})`" name="issue">
              <el-table :data="d.issues" size="small" border>
                <el-table-column prop="issueTypeLabel" label="类型" width="130" />
                <el-table-column prop="description" label="描述" min-width="160" />
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status==='RESOLVED'?'success':'danger'">
                      {{ row.status==='RESOLVED'?'已闭环':'待处理' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="resolveNote" label="闭环说明" min-width="140" />
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button v-if="row.status==='OPEN'" link type="primary" @click="resolveIssue(row)">闭环</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <!-- 各类操作弹窗由 ActionDialogs 承载 -->
    <action-dialogs ref="dialogs" :student-id="d.id" @done="reload" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { api } from '../api'
import ActionDialogs from '../components/ActionDialogs.vue'

const route = useRoute()
const d = ref({})
const timelineRows = ref([])
const tab = ref('screen')
const dialogs = ref()

const load = async () => {
  d.value = await api.studentDetail(route.params.id)
  timelineRows.value = [...(d.value.events || [])].reverse()
}
const reload = async () => { await load(); ElMessage.success('操作已记录到健康管理时间线') }

// 当前待录入结果的复测（PENDING）
const pendingRetest = computed(() =>
  (d.value.retests || []).find(r => r.status === 'PENDING') || null)
// 是否可在原记录上发起复测：存在异常首筛、且无待办复测
const canRetestNow = computed(() => {
  const hasAbnormalFirst = (d.value.screens || []).some(s => s.screenRound !== 'RETEST' && s.abnormal)
  return hasAbnormalFirst || !!pendingRetest.value
})

const openDialog = (name, ctx) => {
  if (name === 'retestSubmit' && !ctx && pendingRetest.value) {
    ctx = {
      retestId: pendingRetest.value.id,
      deviceCalibNo: pendingRetest.value.originalScreen?.deviceCalibNo,
      venue: pendingRetest.value.originalScreen?.venue
    }
  }
  dialogs.value.open(name, ctx)
}
const fmt = (t) => t ? t.replace('T', ' ').substring(0, 16) : ''
const dotType = (t) => ({
  SCREEN: 'primary', VISIT: 'success', PRESCRIPTION: 'success', SUBSIDY: 'warning',
  ISSUE: 'danger', RESOLVE: 'success', INTERVENTION: 'info', STAGE_CHANGE: ''
}[t] || '')
const tagType = (t) => ({
  SCREEN: 'primary', VISIT: 'success', PRESCRIPTION: 'success', SUBSIDY: 'warning',
  ISSUE: 'danger', RESOLVE: 'success', INTERVENTION: 'info', NOTIFY: '', PARENT_CONFIRM: '',
  SCHOOL_REVIEW: '', RETEST: 'warning', GLASSES: '', REVIEW: '', IMPORT: '', STAGE_CHANGE: 'info'
}[t] || 'info')

const resolveIssue = (row) => {
  ElMessageBox.prompt('请填写闭环处理说明', '闭环异常事项', {
    confirmButtonText: '确认闭环', cancelButtonText: '取消', inputType: 'textarea'
  }).then(async ({ value }) => {
    await api.resolveIssue({ issueId: row.id, resolveNote: value || '已处理' })
    ElMessage.success('事项已闭环')
    load()
  }).catch(() => {})
}

const resolveDiscomfort = (g) => {
  ElMessageBox.prompt('请填写复诊调整情况', '配镜后不适处理', {
    confirmButtonText: '确认', cancelButtonText: '取消', inputType: 'textarea'
  }).then(async ({ value }) => {
    await api.resolveDiscomfort(g.id, value || '已复诊调整')
    ElMessage.success('已登记并闭环')
    load()
  }).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.actions { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
.tl { max-height: 640px; overflow-y: auto; padding-right: 6px; }
</style>
