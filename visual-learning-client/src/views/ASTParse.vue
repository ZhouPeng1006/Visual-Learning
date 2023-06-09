<template>
  <div id="container">
    <div id="control-panel">
      <button id="run-button" @click="sendCodeToBackend">Run</button>
    </div>
    <div id="content">
      <div id="code-area">
        <div id="line-numbers">
          <div v-for="lineNumber in codeLines" :key="lineNumber">{{ lineNumber }}</div>
        </div>
        <textarea id="code-input" rows="10" v-model="code" @input="updateLineNumbers"></textarea>
      </div>
      <div id="resizer" @mousedown="initResize"></div>
      <div id="tree-area">
        <svg id="tree-svg">
          <!-- A test  -->
          <rect x="50" y="50" width="100" height="100" fill="blue" />
        </svg>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";
export default {
  data() {
    return {
      code: '',
      codeLines: [],
    };
  },
  mounted() {
    this.updateLineNumbers();
  },
  methods: {
    initResize() {
      window.addEventListener('mousemove', this.resize);
      window.addEventListener('mouseup', this.stopResize);
    },
    resize(event) {
      const x = event.pageX;
      // const codeAreaWidth = this.$refs.codeArea.offsetWidth;
      const codeAreaWidth = document.getElementById('code-area').offsetWidth;
      const treeAreaWidth = document.getElementById('tree-area').offsetWidth;
      // const treeAreaWidth = this.$refs.treeArea.offsetWidth;
      const containerWidth = codeAreaWidth + treeAreaWidth;

      const newCodeAreaWidth = codeAreaWidth + (x - codeAreaWidth);
      const newTreeAreaWidth = treeAreaWidth - (x - codeAreaWidth);

      if (newCodeAreaWidth > 0 && newTreeAreaWidth > 0 && containerWidth > 0) {
        // this.$refs.codeArea.style.flexBasis = `${(newCodeAreaWidth / containerWidth) * 100}%`;
        document.getElementById('code-area').style.flexBasis = `${(newCodeAreaWidth / containerWidth) * 100}%`;
        document.getElementById('tree-area').style.flexBasis = `${(newTreeAreaWidth / containerWidth) * 100}%`;
        // this.$refs.treeArea.style.flexBasis = `${(newTreeAreaWidth / containerWidth) * 100}%`;
      }
    },
    stopResize() {
      window.removeEventListener('mousemove', this.resize);
      window.removeEventListener('mouseup', this.stopResize);
    },
    updateLineNumbers() {
      const codeLines = this.code.split('\n');
      this.codeLines = codeLines.map((_, index) => index + 1);
    },
    sendCodeToBackend() {
      const data = {
        code: this.code,
      };
      // const jsonData = JSON.stringify(data);
      axios.post('/parse', JSON.stringify(data),{
        headers: {
          'Content-Type': 'application/json',
        },
      })
          .then((response) => {
            // 处理后端返回的结果
            if (response.data === 'OK') {
              console.log(response.data + "1")
            }
            // console.log(response.data);
          })
          .catch((error) => {
            // 处理错误
            console.error('Error:', error);
          });



    },
  },
};
</script>

<style scoped src="../assets/ASTVisio.css">
</style>