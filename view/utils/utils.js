// All timestamps from the services are Unix seconds (team decision)
exports.toMs = (ts) => ts * 1000;
exports.fmtDate = (ts) => (ts ? new Date(ts * 1000).toLocaleString() : '');