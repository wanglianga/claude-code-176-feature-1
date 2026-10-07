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
          <el-button type="primary" plain @click="openDialog('review')">学校复核</el-button>
          <el-button type="warning" plain @click="openDialog('retestInitiate')">发起复测</el-button>
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
            <el-tab-pane label="筛查记录" name="screen">
              <el-descriptions v-if="d.latestScreen" :column="2" border size="small">
                <el-descriptions-item label="筛查日期">{{ d.latestScreen.screenDate }}</el-descriptions-item>
                <el-descriptions-item label="请假未检">
                  <el-tag v-if="d.latestScreen.absent" type="danger">是</el-tag>
                  <span v-else>否</span>
                </el-descriptions-item>
                <el-descriptions-item label="裸眼视力(左/右)">{{ d.latestScreen.nakedVisionLeft }} / {{ d.latestScreen.nakedVisionRight }}</el-descriptions-item>
                <el-descriptions-item label="球镜(左/右)">{{ d.latestScreen.sphereLeft }} / {{ d.latestScreen.sphereRight }}</el-descriptions-item>
                <el-descriptions-item label="散光柱镜(左/右)">{{ d.latestScreen.cylinderLeft }} / {{ d.latestScreen.cylinderRight }}</el-descriptions-item>
                <el-descriptions-item label="是否戴镜">{{ d.latestScreen.wearingGlasses ? '是' : '否' }}</el-descriptions-item>
                <el-descriptions-item label="设备校准编号">{{ d.latestScreen.deviceCalibNo }}</el-descriptions-item>
                <el-descriptions-item label="操作人">{{ d.latestScreen.orgName }} {{ d.latestScreen.operatorName }}</el-descriptions-item>
                <el-descriptions-item label="复测建议">
                  <el-tag v-if="d.latestScreen.retestNeeded" type="warning">需要复测</el-tag><span v-else>否</span>
                </el-descriptions-item>
                <el-descriptions-item label="判定">
                  <el-tag v-if="d.latestScreen.abnormal" type="danger">异常：{{ d.latestScreen.abnormalReason }}</el-tag>
                  <el-tag v-else type="success">正常</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="用眼习惯问卷" :span="2">{{ d.latestScreen.habitSummary || '—' }}</el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="暂无筛查记录" :image-size="60" />
              <template v-if="d.screens?.length > 1">
                <el-divider content-position="left">全部筛查记录（初测/复测均保留）</el-divider>
                <el-table :data="d.screens" size="small" border>
                  <el-table-column prop="screenDate" label="日期" width="105" />
                  <el-table-column label="轮次" width="80">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.screenRound === 'RETEST' ? 'warning' : 'info'">
                        {{ row.screenRound === 'RETEST' ? '复测' : '初测' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="裸眼视力(左/右)" width="130">
                    <template #default="{ row }">{{ row.nakedVisionLeft || '—' }} / {{ row.nakedVisionRight || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="球镜(左/右)" width="120">
                    <template #default="{ row }">{{ row.sphereLeft || '—' }} / {{ row.sphereRight || '—' }}</template>
                  </el-table-column>
                  <el-table-column prop="deviceCalibNo" label="设备编号" width="130" />
                  <el-table-column prop="operatorName" label="操作人" width="110" />
                  <el-table-column label="判定" width="90">
                    <template #default="{ row }">
                      <el-tag v-if="row.absent" size="small" type="info">请假</el-tag>
                      <el-tag v-else-if="row.abnormal" size="small" type="danger">异常</el-tag>
                      <el-tag v-else size="small" type="success">正常</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
            </el-tab-pane>

            <el-tab-pane :label="`复测二次确认(${d.retestTasks?.length || 0})`" name="retest">
              <div v-for="t in d.retestTasks" :key="t.id" style="margin-bottom:10px">
                <el-card shadow="never">
                  <div>
                    <el-tag size="small" :type="t.status === 'PENDING' ? 'warning' : t.status === 'CLEARED_NORMAL' ? 'success' : 'danger'">
                      {{ t.statusLabel }}
                    </el-tag>
                    <el-tag size="small" type="warning" effect="plain" style="margin-left:6px">{{ t.reasonLabel }}</el-tag>
                    <span class="muted" style="margin-left:8px">{{ t.reasonDetail }}</span>
                  </div>
                  <el-row :gutter="10" style="margin-top:8px">
                    <el-col :span="11">
                      <div class="muted">初测（{{ t.originalScreen?.screenDate }} ｜ 设备 {{ t.originalScreen?.deviceCalibNo || '—' }} ｜ {{ t.originalScreen?.operatorName || '—' }}）</div>
                      <div>裸眼 左{{ t.originalScreen?.nakedVisionLeft || '—' }}/右{{ t.originalScreen?.nakedVisionRight || '—' }}，
                        球镜 左{{ t.originalScreen?.sphereLeft || '—' }}/右{{ t.originalScreen?.sphereRight || '—' }}
                        <el-tag size="small" type="danger" style="margin-left:4px">异常</el-tag>
                      </div>
                    </el-col>
                    <el-col :span="11" v-if="t.retestScreen">
                      <div class="muted">复测（{{ t.retestScreen.screenDate }} ｜ 设备 {{ t.retestScreen.deviceCalibNo || '—' }} ｜ {{ t.retestScreen.operatorName || '—' }}）</div>
                      <div>裸眼 左{{ t.retestScreen.nakedVisionLeft || '—' }}/右{{ t.retestScreen.nakedVisionRight || '—' }}，
                        球镜 左{{ t.retestScreen.sphereLeft || '—' }}/右{{ t.retestScreen.sphereRight || '—' }}
                        <el-tag size="small" :type="t.retestScreen.abnormal ? 'danger' : 'success'" style="margin-left:4px">
                          {{ t.retestScreen.abnormal ? '仍异常' : '正常' }}
                        </el-tag>
                      </div>
                    </el-col>
                  </el-row>
                  <el-alert v-if="t.conclusion" style="margin-top:6px" :closable="false"
                    :type="t.status === 'CLEARED_NORMAL' ? 'success' : t.status === 'PENDING' ? 'warning' : 'error'"
                    :title="'复测结论：' + t.conclusion" />
                  <div class="muted" style="margin-top:4px">发起人：{{ t.initiator }}</div>
                  <el-button v-if="t.status === 'PENDING'" size="small" type="warning" style="margin-top:6px"
                             @click="completeRetest(t)">录入复测结果</el-button>
                </el-card>
              </div>
              <el-empty v-if="!d.retestTasks?.length" description="暂无复测记录" :image-size="60" />
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
import { ref, onMounted } from 'vue'
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

const openDialog = (name) => dialogs.value.open(name)
const completeRetest = (t) => dialogs.value.open('retestComplete', { taskId: t.id, original: t.originalScreen })
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
