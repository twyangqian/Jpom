<template>
  <div>
    <a-form
      ref="releaseFileForm"
      :rules="releaseFileRules"
      :model="temp"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 20 }"
    >
      <a-form-item label="任务名" prop="name">
        <a-input placeholder="请输入任务名" :maxLength="50" v-model="temp.name" />
      </a-form-item>

      <a-form-item label="发布方式" prop="taskType">
        <a-radio-group v-model="temp.taskType" @change="taskTypeChange">
          <a-radio :value="0"> SSH </a-radio>
          <a-radio :value="1"> 节点 </a-radio>
        </a-radio-group>
      </a-form-item>

      <a-form-item prop="taskDataIds" label="发布的SSH" v-if="temp.taskType === 0">
        <a-row>
          <a-col :span="22">
            <a-select
              show-search
              option-filter-prop="children"
              mode="multiple"
              v-model="temp.taskDataIds"
              placeholder="请选择SSH"
            >
              <a-select-option v-for="ssh in sshList" :key="ssh.id">
                <a-tooltip :title="ssh.name"> {{ ssh.name }}</a-tooltip>
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :span="1" style="margin-left: 10px">
            <a-icon type="reload" @click="loadSshList" />
          </a-col>
        </a-row>
      </a-form-item>
      <a-form-item prop="taskDataIds" label="发布的节点" v-else-if="temp.taskType === 1">
        <a-row>
          <a-col :span="22">
            <a-select
              show-search
              option-filter-prop="children"
              mode="multiple"
              v-model="temp.taskDataIds"
              placeholder="请选择节点"
            >
              <a-select-option v-for="ssh in nodeList" :key="ssh.id">
                <a-tooltip :title="ssh.name"> {{ ssh.name }}</a-tooltip>
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :span="1" style="margin-left: 10px">
            <a-icon type="reload" @click="loadNodeList" />
          </a-col>
        </a-row>
      </a-form-item>

      <a-form-item prop="releasePathParent" label="发布目录">
        <template #help>
          <a-tooltip title="需要配置授权目录（白名单才能正常使用发布）,授权目录主要是用于确定可以发布到哪些目录中"
            ><a-button
              icon="info-circle"
              size="small"
              type="link"
              @click="
                () => {
                  this.configDir = true
                }
              "
            >
              配置目录
            </a-button>
          </a-tooltip></template
        >
        <a-input-group compact>
          <a-select
            show-search
            allowClear
            style="width: 30%"
            v-model="temp.releasePathParent"
            placeholder="请选择发布的一级目录"
          >
            <a-select-option v-for="item in accessList" :key="item">
              <a-tooltip :title="item">{{ item }}</a-tooltip>
            </a-select-option>
            <a-icon #suffixIcon type="reload" @click="loadAccesList" />
          </a-select>

          <a-input style="width: 70%" v-model="temp.releasePathSecondary" placeholder="请填写发布的二级目录" />
        </a-input-group>
      </a-form-item>

      <a-form-item label="执行脚本" prop="releaseBeforeCommand">
        <a-tabs tabPosition="right">
          <a-tab-pane key="before" tab="上传前">
            <div style="height: 40vh; overflow-y: scroll">
              <code-editor
                v-model="temp.beforeScript"
                :options="{ mode: temp.taskType === 0 ? 'shell' : '', tabSize: 2, theme: 'abcdef' }"
              ></code-editor>
            </div>
            <div style="margin-top: 10px">文件上传前需要执行的脚本(非阻塞命令)</div>
          </a-tab-pane>
          <a-tab-pane key="after" tab="上传后">
            <div style="height: 40vh; overflow-y: scroll">
              <code-editor
                v-model="temp.afterScript"
                :options="{ mode: temp.taskType === 0 ? 'shell' : '', tabSize: 2, theme: 'abcdef' }"
              ></code-editor>
            </div>
            <div style="margin-top: 10px">文件上传成功后需要执行的脚本(非阻塞命令)</div>
          </a-tab-pane>
        </a-tabs>
      </a-form-item>
    </a-form>

    <a-modal
      destroyOnClose
      v-model="configDir"
      :title="`配置授权目录`"
      :footer="null"
      :maskClosable="false"
      @cancel="
        () => {
          this.configDir = false
        }
      "
    >
      <whiteList
        v-if="configDir"
        @cancel="
          () => {
            this.configDir = false
            this.loadAccesList()
          }
        "
      ></whiteList>
    </a-modal>
  </div>
</template>
<script>
import { getSshListAll } from '@/api/ssh'
import { getDispatchWhiteList } from '@/api/dispatch'
import { getNodeListAll } from '@/api/node'
import codeEditor from '@/components/codeEditor'
import whiteList from '@/pages/dispatch/white-list.vue'
export default {
  components: {
    codeEditor,
    whiteList
  },
  data() {
    return {
      temp: {},
      releaseFileRules: {
        name: [{ required: true, message: '请输入文件任务名', trigger: 'blur' }],
        taskType: [{ required: true, message: '请选择发布方式', trigger: 'blur' }],
        releasePath: [{ required: true, message: '请选择发布的一级目录和填写二级目录', trigger: 'blur' }],
        taskDataIds: [{ required: true, message: '请选择发布的SSH', trigger: 'blur' }]
      },
      sshList: [],
      accessList: [],
      nodeList: [],
      configDir: false
    }
  },
  created() {
    this.temp = { taskType: 0 }
    this.taskTypeChange(0)
    this.loadAccesList()
  },
  methods: {
    taskTypeChange() {
      const value = this.temp.taskType
      this.temp = { ...this.temp, taskDataIds: undefined }
      if (value === 0) {
        this.loadSshList()
      } else if (value === 1) {
        this.loadNodeList()
      }
    },
    // 创建任务
    tryCommit() {
      this.$refs['releaseFileForm'].validate((valid) => {
        if (!valid) {
          return false
        }
        this.$emit('commit', { ...this.temp, taskDataIds: this.temp.taskDataIds?.join(',') })
      })
    },
    // 加载项目白名单列表
    loadAccesList() {
      getDispatchWhiteList().then((res) => {
        if (res.code === 200) {
          this.accessList = res.data.outGivingArray || []
        }
      })
    },
    // 加载 SSH 列表
    loadSshList() {
      return new Promise((resolve) => {
        this.sshList = []
        getSshListAll().then((res) => {
          if (res.code === 200) {
            this.sshList = res.data
            resolve()
          }
        })
      })
    },
    // 加载节点
    loadNodeList() {
      getNodeListAll().then((res) => {
        if (res.code === 200) {
          this.nodeList = res.data
        }
      })
    }
  }
}
</script>
