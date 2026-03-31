#!/usr/bin/env python3
"""
使用本地AI模型翻译strings.xml文件
支持Hugging Face模型，如Qwen3.5 9b
"""

import os
import re
import json
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import argparse
from typing import Dict, List, Tuple


class LocalAITranslator:
    def __init__(self, model_name='Qwen/Qwen2.5-7B-Instruct', device='cuda'):
        self.model_name = model_name
        self.device = device
        self.translated_cache = {}
        self.model = None
        self.tokenizer = None
        
        # 尝试导入必要的库
        try:
            from transformers import AutoTokenizer, AutoModelForCausalLM
            import torch
            
            print(f"加载模型: {model_name}")
            self.tokenizer = AutoTokenizer.from_pretrained(model_name)
            self.model = AutoModelForCausalLM.from_pretrained(
                model_name,
                torch_dtype=torch.float16 if device == 'cuda' else torch.float32,
                device_map=device if device == 'cuda' else None
            )
            print("模型加载成功")
            
        except ImportError as e:
            print(f"警告: 无法加载transformers库: {e}")
            print("请安装: pip install transformers torch")
        except Exception as e:
            print(f"警告: 无法加载模型: {e}")
    
    def translate_text(self, text, source_lang, target_lang):
        """翻译文本"""
        if not text or text.strip() == '':
            return text
            
        # 检查缓存
        cache_key = f"{text}_{source_lang}_{target_lang}"
        if cache_key in self.translated_cache:
            return self.translated_cache[cache_key]
        
        # 如果模型未加载，使用简单的翻译（仅用于测试）
        if self.model is None:
            print(f"警告: 模型未加载，使用占位符翻译")
            translated = f"[{target_lang}] {text}"
            self.translated_cache[cache_key] = translated
            return translated
        
        try:
            # 构建提示词
            prompt = f"请将以下文本从{source_lang}翻译成{target_lang}：\n\n{text}\n\n翻译结果："
            
            # 编码输入
            inputs = self.tokenizer.encode(prompt, return_tensors='pt').to(self.device)
            
            # 生成翻译
            with torch.no_grad():
                outputs = self.model.generate(
                    inputs,
                    max_new_tokens=200,
                    temperature=0.3,
                    do_sample=True,
                    pad_token_id=self.tokenizer.eos_token_id
                )
            
            # 解码输出
            translated = self.tokenizer.decode(outputs[0], skip_special_tokens=True)
            
            # 提取翻译结果（去除提示词部分）
            if '翻译结果：' in translated:
                translated = translated.split('翻译结果：')[-1].strip()
            
            # 缓存结果
            self.translated_cache[cache_key] = translated
            return translated
            
        except Exception as e:
            print(f"翻译失败: {e}")
            return text


class StringsTranslator:
    def __init__(self, base_file: str, translator: LocalAITranslator):
        self.base_file = Path(base_file)
        self.translator = translator
        self.base_strings = {}
        
    def parse_base_file(self):
        """解析基础strings.xml文件"""
        tree = ET.parse(self.base_file)
        root = tree.getroot()
        
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name:
                    self.base_strings[name] = elem.text or ''
            elif elem.tag == 'plurals':
                name = elem.get('name')
                if name:
                    self.base_strings[f'plurals_{name}'] = elem
    
    def get_language_name(self, dir_name):
        """根据目录名获取语言名称"""
        language_names = {
            'values-de': '德语',
            'values-en': '英语',
            'values-es': '西班牙语',
            'values-fr': '法语',
            'values-ja': '日语',
            'values-ko': '韩语',
            'values-th': '泰语',
            'values-zh-rCN': '简体中文',
            'values-zh-rHK': '繁体中文（香港）',
            'values-zh-rTW': '繁体中文（台湾）',
        }
        
        return language_names.get(dir_name, '目标语言')
    
    def translate_file(self, file_path: Path, dry_run=False):
        """翻译文件"""
        if not file_path.exists():
            print(f"文件不存在: {file_path}")
            return
        
        # 获取语言名称
        dir_name = file_path.parent.name
        target_lang = self.get_language_name(dir_name)
        
        print(f"翻译 {dir_name} ({target_lang})")
        
        # 解析文件
        tree = ET.parse(file_path)
        root = tree.getroot()
        
        translated_count = 0
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name and name in self.base_strings:
                    base_text = self.base_strings[name]
                    current_text = elem.text or ''
                    
                    # 检查是否需要翻译
                    if current_text != base_text:
                        # 翻译文本
                        if not dry_run:
                            translated_text = self.translator.translate_text(
                                base_text, '英语', target_lang
                            )
                            elem.text = translated_text
                        else:
                            translated_text = f"[翻译] {base_text}"
                        
                        translated_count += 1
                        print(f"  {name}: {current_text} -> {translated_text}")
            
            elif elem.tag == 'plurals':
                name = elem.get('name')
                if name and f'plurals_{name}' in self.base_strings:
                    base_elem = self.base_strings[f'plurals_{name}']
                    if isinstance(base_elem, ET.Element):
                        for item in elem:
                            quantity = item.get('quantity')
                            # 找到基础文件中对应的item
                            for base_item in base_elem:
                                if base_item.get('quantity') == quantity:
                                    base_text = base_item.text or ''
                                    current_text = item.text or ''
                                    
                                    if current_text != base_text:
                                        if not dry_run:
                                            translated_text = self.translator.translate_text(
                                                base_text, '英语', target_lang
                                            )
                                            item.text = translated_text
                                        else:
                                            translated_text = f"[翻译] {base_text}"
                                        
                                        translated_count += 1
                                        print(f"  {name} ({quantity}): {current_text} -> {translated_text}")
        
        if not dry_run:
            # 格式化并保存
            self._indent_xml(root)
            tree.write(file_path, encoding='utf-8', xml_declaration=True)
            print(f"  已保存，翻译了 {translated_count} 个字符串")
        else:
            print(f"  模拟运行，将翻译 {translated_count} 个字符串")
    
    def _indent_xml(self, elem, level=0):
        """为XML元素添加缩进"""
        i = "\n" + level * "    "
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = i + "    "
            if not elem.tail or not elem.tail.strip():
                elem.tail = i
            for child in elem:
                self._indent_xml(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = i
        else:
            if level and (not elem.tail or not elem.tail.strip()):
                elem.tail = i
    
    def process_all_languages(self, dry_run=False):
        """处理所有语言文件"""
        res_dir = self.base_file.parent.parent
        
        # 获取所有语言目录
        language_dirs = []
        for item in res_dir.iterdir():
            if item.is_dir() and item.name.startswith('values-') and item.name != 'values':
                # 排除配置特定的覆盖文件
                config_patterns = ['values-land', 'values-large', 'values-night', 'values-sw', 'values-v']
                is_config_override = any(item.name.startswith(pattern) for pattern in config_patterns)
                if not is_config_override:
                    language_dirs.append(item)
        
        print(f"找到 {len(language_dirs)} 个语言目录")
        
        for lang_dir in language_dirs:
            lang_file = lang_dir / 'strings.xml'
            if lang_file.exists():
                self.translate_file(lang_file, dry_run)


def find_base_strings_file():
    """自动查找基础strings.xml文件"""
    # 尝试在当前目录和父目录中查找
    search_paths = [
        Path.cwd() / 'app' / 'src' / 'main' / 'res' / 'values' / 'strings.xml',
        Path.cwd().parent / 'app' / 'src' / 'main' / 'res' / 'values' / 'strings.xml',
        Path.cwd() / 'app' / 'src' / 'main' / 'res' / 'values' / 'strings.xml',
    ]
    
    for path in search_paths:
        if path.exists():
            return path
    
    # 如果没有找到，尝试在res目录中查找
    res_dirs = []
    for path in [Path.cwd(), Path.cwd().parent]:
        if (path / 'app' / 'src' / 'main' / 'res').exists():
            res_dirs.append(path / 'app' / 'src' / 'main' / 'res')
    
    for res_dir in res_dirs:
        values_dir = res_dir / 'values'
        if values_dir.exists():
            strings_file = values_dir / 'strings.xml'
            if strings_file.exists():
                return strings_file
    
    return None


def main():
    parser = argparse.ArgumentParser(description='使用本地AI模型翻译strings.xml文件')
    parser.add_argument('--base', help='基础strings.xml文件路径（可选，会自动查找）')
    parser.add_argument('--model', default='Qwen/Qwen2.5-7B-Instruct', 
                       help='Hugging Face模型名称')
    parser.add_argument('--device', default='cuda', 
                       choices=['cuda', 'cpu'],
                       help='运行设备')
    parser.add_argument('--dry-run', action='store_true', 
                       help='只显示将要进行的翻译，不实际修改文件')
    
    args = parser.parse_args()
    
    # 查找基础文件
    if args.base:
        base_file = Path(args.base)
        if not base_file.exists():
            print(f"错误: 指定的基础文件不存在: {args.base}")
            return 1
    else:
        base_file = find_base_strings_file()
        if not base_file:
            print("错误: 无法自动找到基础strings.xml文件")
            print("请使用 --base 参数指定文件路径")
            return 1
        print(f"自动找到基础文件: {base_file}")
    
    # 创建翻译器
    translator = LocalAITranslator(args.model, args.device)
    
    # 创建字符串翻译器
    strings_translator = StringsTranslator(str(base_file), translator)
    strings_translator.parse_base_file()
    
    print(f"基础文件: {base_file}")
    print(f"找到 {len(strings_translator.base_strings)} 个基础字符串")
    print(f"使用模型: {args.model}")
    print(f"运行设备: {args.device}")
    
    # 处理所有语言文件
    strings_translator.process_all_languages(dry_run=args.dry_run)
    
    if args.dry_run:
        print("\n注意: 这是模拟运行，没有实际修改文件")
    
    return 0


if __name__ == '__main__':
    exit(main())