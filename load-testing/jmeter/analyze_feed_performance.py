import csv
import json
from collections import defaultdict, OrderedDict
from datetime import datetime
import statistics

# File path
jmeter_file = r"E:\fanout_hybrid_system\load-testing\jmeter\results\1hr-load-test.jtl"

# Data structures
feed_requests = []
other_requests = defaultdict(list)
all_requests = []
timeline_data = defaultdict(lambda: defaultdict(list))  # timestamp -> label -> response times

print("Reading JMeter results file...")
with open(jmeter_file, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        try:
            elapsed = int(row['elapsed'])
            timestamp = int(row['timeStamp'])
            label = row['label']
            success = row['success'] == 'true'
            response_code = row['responseCode']
            failure_msg = row['failureMessage']
            
            request_data = {
                'timestamp': timestamp,
                'elapsed': elapsed,
                'label': label,
                'success': success,
                'response_code': response_code,
                'failure_msg': failure_msg,
                'url': row['URL']
            }
            
            all_requests.append(request_data)
            
            # Categorize
            if 'Feed' in label:
                feed_requests.append(request_data)
            else:
                other_requests[label].append(request_data)
            
            # Timeline
            time_bucket = (timestamp // 10000) * 10000  # 10-second buckets
            timeline_data[time_bucket][label].append(elapsed)
        except Exception as e:
            print(f"Error processing row: {e}")
            continue

print(f"Total requests: {len(all_requests)}")
print(f"Feed requests: {len(feed_requests)}")
print(f"Other request types: {len(other_requests)}")
print()

# Analyze feed requests by type
feed_types = defaultdict(list)
for req in feed_requests:
    label = req['label']
    feed_types[label].append(req)

print("=" * 80)
print("FEED REQUEST ANALYSIS")
print("=" * 80)

for feed_type in sorted(feed_types.keys()):
    reqs = feed_types[feed_type]
    times = [r['elapsed'] for r in reqs]
    successes = sum(1 for r in reqs if r['success'])
    failures = len(reqs) - successes
    
    print(f"\n{feed_type}:")
    print(f"  Total requests: {len(reqs)}")
    print(f"  Success count: {successes} ({100*successes/len(reqs):.1f}%)")
    print(f"  Failed count: {failures}")
    
    if times:
        print(f"  Response times (ms):")
        print(f"    Min:     {min(times):>6.0f}")
        print(f"    Max:     {max(times):>6.0f}")
        print(f"    Mean:    {statistics.mean(times):>6.1f}")
        print(f"    Median:  {statistics.median(times):>6.1f}")
        
        # Percentiles
        sorted_times = sorted(times)
        p95_idx = int(len(sorted_times) * 0.95)
        p99_idx = int(len(sorted_times) * 0.99)
        print(f"    P95:     {sorted_times[p95_idx]:>6.0f}")
        print(f"    P99:     {sorted_times[p99_idx]:>6.0f}")
        
        # Outliers (> 2000ms)
        outliers = [t for t in times if t > 2000]
        if outliers:
            print(f"  Outliers (>2000ms): {len(outliers)} requests")
            for outlier in sorted(outliers, reverse=True)[:5]:
                print(f"    {outlier}ms")

# Baseline comparison
print("\n" + "=" * 80)
print("BASELINE COMPARISON")
print("=" * 80)

baseline = {
    'GET User Profile': 921,
    'GET Following': 931,
    'GET Followers': 916,
}

print("\nComparison to baseline (<1000ms expected):")
for label, baseline_ms in baseline.items():
    if label in other_requests:
        reqs = other_requests[label]
        times = [r['elapsed'] for r in reqs]
        actual_mean = statistics.mean(times)
        ratio = actual_mean / baseline_ms
        print(f"{label}:")
        print(f"  Expected: {baseline_ms}ms")
        print(f"  Actual:   {actual_mean:.0f}ms")
        print(f"  Ratio:    {ratio:.2f}x")

if 'GET User Feed' in feed_types:
    feed_times = [r['elapsed'] for r in feed_types['GET User Feed']]
    feed_mean = statistics.mean(feed_times)
    expected_feed = 1000
    print(f"GET User Feed:")
    print(f"  Expected: {expected_feed}ms")
    print(f"  Actual:   {feed_mean:.0f}ms")
    print(f"  Ratio:    {feed_mean/expected_feed:.2f}x")

# Timeline analysis
print("\n" + "=" * 80)
print("TIMELINE ANALYSIS (10-second buckets)")
print("=" * 80)

print("\nFeed request performance over time:")
print(f"{'Time Bucket':<15} {'Count':<8} {'Mean (ms)':<12} {'Max (ms)':<10}")
print("-" * 50)

for time_bucket in sorted(timeline_data.keys()):
    for label in ['GET User Feed', 'GET User Feed (Before Post)']:
        if label in timeline_data[time_bucket]:
            times = timeline_data[time_bucket][label]
            if times:
                bucket_time = datetime.fromtimestamp(time_bucket/1000).strftime('%H:%M:%S')
                print(f"{bucket_time:<15} {len(times):<8} {statistics.mean(times):<12.1f} {max(times):<10.0f}")

# Failure analysis
print("\n" + "=" * 80)
print("FAILURE ANALYSIS")
print("=" * 80)

failures_by_type = defaultdict(lambda: defaultdict(int))
for req in all_requests:
    if not req['success']:
        failures_by_type[req['label']][req['failure_msg']] += 1

if failures_by_type:
    for label, failures in failures_by_type.items():
        print(f"\n{label}:")
        for failure_msg, count in sorted(failures.items(), key=lambda x: x[1], reverse=True):
            print(f"  {failure_msg}: {count}")
else:
    print("No failures recorded")

print("\n" + "=" * 80)
print("ANALYSIS COMPLETE")
print("=" * 80)
